package tfdev.dice

import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.GLRender3DView
import tfdev.engine3d.gpu.GLRenderer
import tfdev.engine3d.gpu.gl_object3d.GLCube
import tfdev.engine3d.gpu.gl_object3d.GLObject3D
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var renderView: GLRender3DView
    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var linearAccelerationSensor: Sensor? = null
    private lateinit var appSettings: AppSettings
    private lateinit var diceColorRow: DiceColorRow
    private lateinit var rollHistoryOverlay: RollHistoryOverlay

    // Newest die first - matches the order newly-added dice are shown in the "Dice
    // colors" settings row. Also the one source of truth for room sizing, spawn
    // clamping and removal on reset - there's no separate plain list of dice.
    private val diceEntries = mutableListOf<DieEntry>()
    private val roomPieces = mutableListOf<GLObject3D>()

    private var rollHistoryEnabled = true

    // Rest-detection state for the roll-history capture - see checkRollSettledOnGlThread.
    private var restSinceMillis = 0L
    private var pendingRollCapture = true

    // Guards the layout-change-triggered rebuild (see onCreate) against firing on the
    // very first layout pass, before the GL surface/context exists - the initial room
    // build already happens via setOnSceneInitializedListener below, which is the only
    // thing allowed to touch GLCube/shaders before this is true.
    @Volatile private var sceneInitialized = false

    /** Each room surface together with the (fixed) saturation/value it keeps while the hue drifts. */
    private data class RoomSurface(val piece: GLObject3D, val saturation: Float, val value: Float)
    private val roomSurfaces = mutableListOf<RoomSurface>()

    private val colorCycleHandler = Handler(Looper.getMainLooper())
    private val colorCycleTick = object : Runnable {
        override fun run() {
            renderView.queueEvent { recolorRoomSurfacesOnGlThread() }
            colorCycleHandler.postDelayed(this, ROOM_COLOR_UPDATE_INTERVAL_MILLIS)
        }
    }

    private val dieScale = 0.5f

    /** Set by the shake-sensitivity slider; see handleShakeReading. */
    private var shakeSensitivity = DEFAULT_SHAKE_SENSITIVITY

    // Half the floor's walkable footprint along each axis - starts small and grows with
    // the number of dice on the field (see targetRoomFloorArea/rebuildRoomOnGlThread)
    // rather than being one size that has to suit anywhere from 2 dice to a dozen, and
    // isn't necessarily a square: the two are sized to match the screen's own aspect
    // ratio (see GLRenderer.viewportAspect), so a tall screen gets a room that's
    // correspondingly longer top-to-bottom than side-to-side. Used both to build the
    // room and to keep newly-added dice from spawning inside/outside a wall.
    private var roomHalfExtentX = BASE_ROOM_HALF_EXTENT
    private var roomHalfExtentZ = BASE_ROOM_HALF_EXTENT
    private var roomFloorArea = (BASE_ROOM_HALF_EXTENT * 2f) * (BASE_ROOM_HALF_EXTENT * 2f)
    private val wallThickness = 0.2f

    // The camera (and light) height is computed from the screen's actual aspect ratio
    // so the room's footprint fits on screen on any device (see GLRenderer -
    // recomputeFraming), capped at MAX_EYE_DISTANCE - so the ceiling just needs to
    // clear that cap, not any one particular computed height.
    private val roomHeight = GLRenderer.MAX_EYE_DISTANCE + 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the 3D scene draw behind the (transparent) status bar instead of being
        // pushed down below it - the controls panel below adds its own matching
        // padding so its buttons/switches don't end up under a system bar instead.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)

        renderView = findViewById(R.id.renderView_activityMain)
        // The camera looks straight down and never orbits, so dragging on the
        // view has nothing to rotate.
        renderView.enableYRotation = false

        diceColorRow = DiceColorRow(this, findViewById(R.id.linearLayout_diceColors_activityMain))
        rollHistoryOverlay = RollHistoryOverlay(this, findViewById(R.id.linearLayout_rollHistory_activityMain))

        val controlsPanel = findViewById<View>(R.id.linearLayout_controls_activityMain)
        val controlsPanelBasePadding = controlsPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(controlsPanel) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = controlsPanelBasePadding + systemBars.bottom)
            insets
        }

        // Without this, the overlay's own touch targets sit under the status bar's
        // touchable strip (even though it's transparent and the content draws behind
        // it) and taps there never reach the overlay at all.
        val rollHistoryContainer = findViewById<View>(R.id.linearLayout_rollHistory_activityMain)
        val rollHistoryBasePadding = rollHistoryContainer.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(rollHistoryContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = rollHistoryBasePadding + systemBars.top)
            insets
        }

        // The room's footprint is sized to match the screen's own aspect ratio (see
        // targetRoomFloorArea/rebuildRoomOnGlThread), so a resize - the settings panel
        // expanding/collapsing, or a rotation - needs the room rebuilt to match it,
        // not just the camera reframed.
        renderView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (sceneInitialized && sizeChanged) {
                renderView.queueEvent { rebuildRoomOnGlThread(renderView.scene, diceEntries.size) }
            }
        }

        // Button clicks land on the UI thread, but creating a die compiles/links an
        // OpenGL shader program, which - like every GL call - only works on the
        // GLSurfaceView's own GL thread; queueEvent hands the work over to it.
        findViewById<Button>(R.id.button_reset_activityMain).setOnClickListener {
            renderView.queueEvent { resetDiceOnGlThread() }
        }
        findViewById<Button>(R.id.button_addDie_activityMain).setOnClickListener {
            renderView.queueEvent { addDieOnGlThread(randomSpawnPosition()) }
        }

        // Collapsed by default - the shake-sensitivity/tilt controls are tweaked rarely,
        // so they shouldn't compete with Reset/Add Die for space or attention.
        val settingsContent = findViewById<View>(R.id.linearLayout_settingsContent_activityMain)
        val settingsToggleText = findViewById<TextView>(R.id.text_settingsToggle_activityMain)
        findViewById<View>(R.id.row_settingsToggle_activityMain).setOnClickListener {
            val expanding = settingsContent.visibility != View.VISIBLE
            settingsContent.visibility = if (expanding) View.VISIBLE else View.GONE
            settingsToggleText.setText(
                if (expanding) R.string.label_settings_expanded else R.string.label_settings_collapsed
            )
        }

        // Restored from the last session (see AppSettings) rather than always starting
        // from the same fixed defaults.
        shakeSensitivity = appSettings.getShakeSensitivity(DEFAULT_SHAKE_SENSITIVITY)
        val tiltEnabled = appSettings.getTiltEnabled(true)
        rollHistoryEnabled = appSettings.getRollHistoryEnabled(true)
        renderView.setCameraTiltEnabled(tiltEnabled)
        rollHistoryOverlay.setEnabled(rollHistoryEnabled)

        findViewById<SeekBar>(R.id.seekBar_shakeSensitivity_activityMain).apply {
            progress = sensitivityToProgress(shakeSensitivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    shakeSensitivity = progressToSensitivity(progress)
                    appSettings.setShakeSensitivity(shakeSensitivity)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<Switch>(R.id.switch_cameraTilt_activityMain).apply {
            isChecked = tiltEnabled
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                renderView.setCameraTiltEnabled(isChecked)
                appSettings.setTiltEnabled(isChecked)
            }
        }

        findViewById<Switch>(R.id.switch_rollHistory_activityMain).apply {
            isChecked = rollHistoryEnabled
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                rollHistoryEnabled = isChecked
                rollHistoryOverlay.setEnabled(isChecked)
                appSettings.setRollHistoryEnabled(isChecked)
            }
        }

        renderView.setOnPhysicsSteppedListener { checkRollSettledOnGlThread() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // This callback is invoked from onSurfaceCreated, i.e. already on the GL thread.
        renderView.setOnSceneInitializedListener { scene ->
            rebuildRoomOnGlThread(scene, diceCount = 0)
            resetDiceOnGlThread()
            sceneInitialized = true
        }
    }

    override fun onResume() {
        super.onResume()
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        colorCycleHandler.post(colorCycleTick)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        colorCycleHandler.removeCallbacks(colorCycleTick)
    }

    /**
     * Two very different things come off the same physical sensor data, split here
     * into two Android sensor types that do the separating for us:
     *
     * - TYPE_GRAVITY is the *slow*, gravity-only component (Android's own sensor
     *   fusion already filters out shake/jitter) - how the device is being *held*.
     *   That only ever retargets the camera's lean (see GLRenderer.updateCameraTilt),
     *   never pushes the dice - a held tilt is not supposed to act like a sustained
     *   sideways gravity on the dice themselves, just like looking at a tilted table
     *   from a tilted angle.
     * - TYPE_LINEAR_ACCELERATION is the device's own acceleration with gravity
     *   already removed - i.e. exactly the *shaking* component. That's what pushes
     *   the dice, and with real torque, not just a slide (see GLScene.applyExternalAcceleration).
     */
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> handleGravityReading(event.values)
            Sensor.TYPE_LINEAR_ACCELERATION -> handleShakeReading(event.values)
        }
    }

    /**
     * Device X/Y/Z map to world X/up/-Z throughout this file - i.e. this assumes the
     * device is held roughly flat, screen up, matching the top-down camera. If you
     * mostly hold the phone differently, these will need remapping to match.
     */
    private fun handleGravityReading(values: FloatArray) {
        val gx = values[0]
        val gy = values[1]
        val gz = values[2]
        val length = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.01f)
        renderView.setGravityUpDirection(Vec3(gx / length, gz / length, -gy / length))
    }

    /**
     * Treats the device as if it were the cup the dice are rattling around in:
     * accelerating it one way pushes its contents the other way (the same reason a
     * drink sloshes backward when a cup accelerates forward) - see
     * GLScene.applyExternalAcceleration for where that pseudo-force actually gets
     * applied, torque included.
     *
     * [SHAKE_DEADZONE] only lets a deliberate shake through - ordinary handling
     * (picking the phone up, passing it to someone) reads as a few m/s^2 at most and
     * is ignored outright, specifically so a thrown result can't be disturbed by
     * passing the phone around to show people. Subtracting rather than clamping the
     * deadzone also means a shake just past the threshold ramps in gradually instead
     * of starting at full strength.
     */
    private fun handleShakeReading(values: FloatArray) {
        val worldAx = values[0]
        val worldAy = values[2]
        val worldAz = -values[1]
        val magnitude = sqrt(worldAx * worldAx + worldAy * worldAy + worldAz * worldAz)
        val scene = renderView.scene
        if (magnitude < SHAKE_DEADZONE) {
            scene.externalAcceleration.zeros()
            return
        }
        val excess = (magnitude - SHAKE_DEADZONE) / magnitude
        scene.externalAcceleration.x = -worldAx * excess * shakeSensitivity
        scene.externalAcceleration.y = -worldAy * excess * shakeSensitivity
        scene.externalAcceleration.z = -worldAz * excess * shakeSensitivity
    }

    private fun progressToSensitivity(progress: Int): Float {
        val fraction = progress / 100f
        return MIN_SHAKE_SENSITIVITY + fraction * (MAX_SHAKE_SENSITIVITY - MIN_SHAKE_SENSITIVITY)
    }

    private fun sensitivityToProgress(sensitivity: Float): Int {
        val fraction = (sensitivity - MIN_SHAKE_SENSITIVITY) / (MAX_SHAKE_SENSITIVITY - MIN_SHAKE_SENSITIVITY)
        return (fraction * 100f).toInt().coerceIn(0, 100)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * How large the room's floor should be to comfortably fit [diceCount] dice - grows
     * a "working volume" (floor area times roughly a die's height, not the room's full
     * floor-to-ceiling height) linearly with dice count. Most of the room's actual
     * height is empty air the dice never use, so sizing by the full height made the
     * floor grow far too slowly to keep up with added dice; using the dice's own
     * height instead ties growth to the space they actually occupy.
     */
    private fun targetRoomFloorArea(diceCount: Int): Float {
        val extraDice = (diceCount.coerceAtLeast(BASE_DICE_COUNT) - BASE_DICE_COUNT)
        val baseVolume = (BASE_ROOM_HALF_EXTENT * 2f) * (BASE_ROOM_HALF_EXTENT * 2f) * dieScale
        val maxVolume = (MAX_ROOM_HALF_EXTENT * 2f) * (MAX_ROOM_HALF_EXTENT * 2f) * dieScale
        val volume = (baseVolume + extraDice * ROOM_VOLUME_GROWTH_PER_DIE).coerceAtMost(maxVolume)
        return volume / dieScale
    }

    /**
     * Rebuilds the floor/walls/ceiling sized to comfortably fit [diceCount] dice (never
     * smaller than the base size for [BASE_DICE_COUNT]) and reframes the camera/light to
     * match - so the field starts small and close, then grows as more dice join it,
     * rather than being one fixed size that has to suit both 2 dice and a dozen.
     *
     * The floor's area comes from [targetRoomFloorArea], but it's split into X/Z half-
     * extents that match the screen's current aspect ratio rather than always being a
     * square - `halfExtentX / halfExtentZ == aspect`, so a tall screen gets a room
     * that's correspondingly longer top-to-bottom, using the screen edge-to-edge
     * instead of being letterboxed down to its narrower dimension.
     *
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun rebuildRoomOnGlThread(scene: GLScene, diceCount: Int) {
        for (piece in roomPieces) {
            scene.removeChild(piece)
        }
        roomPieces.clear()
        roomSurfaces.clear()

        roomFloorArea = targetRoomFloorArea(diceCount)
        // Read straight from the view's own measured size rather than from the
        // renderer's GL-thread-updated aspect: that field is only refreshed by
        // onSurfaceChanged, which can lag a frame or two behind a layout pass (e.g.
        // the settings panel toggling), momentarily mismatching whatever this method
        // builds the physical walls with.
        val aspect = if (renderView.height > 0) renderView.width.toFloat() / renderView.height.toFloat() else 1f
        roomHalfExtentX = sqrt(roomFloorArea * aspect) / 2f
        roomHalfExtentZ = sqrt(roomFloorArea / aspect) / 2f
        val hue = currentRoomHueDegrees()

        val floor = GLCube(this, color = hsvColor(hue, FLOOR_SATURATION, FLOOR_VALUE)).apply {
            scale.apply {
                x = roomHalfExtentX * 2f
                y = 0.2f
                z = roomHalfExtentZ * 2f
            }
            pos.y = -0.1f
            isStatic = true
            setDirty()
        }
        scene.addChild(floor)
        roomPieces.add(floor)
        roomSurfaces.add(RoomSurface(floor, FLOOR_SATURATION, FLOOR_VALUE))

        val ceilingThickness = 0.2f
        val ceiling = GLCube(this, color = hsvColor(hue, CEILING_SATURATION, CEILING_VALUE)).apply {
            scale.apply {
                x = roomHalfExtentX * 2f
                y = ceilingThickness
                z = roomHalfExtentZ * 2f
            }
            pos.y = roomHeight + ceilingThickness / 2f
            isStatic = true
            setDirty()
        }
        scene.addChild(ceiling)
        roomPieces.add(ceiling)
        roomSurfaces.add(RoomSurface(ceiling, CEILING_SATURATION, CEILING_VALUE))

        val wallColor = hsvColor(hue, WALL_SATURATION, WALL_VALUE)
        val wallCenterOffsetX = roomHalfExtentX + wallThickness / 2f
        val wallCenterOffsetZ = roomHalfExtentZ + wallThickness / 2f
        val wallLengthX = roomHalfExtentX * 2f + wallThickness * 2f
        val wallLengthZ = roomHalfExtentZ * 2f + wallThickness * 2f

        // Walls span the full floor-to-ceiling height, so the room is fully sealed -
        // no amount of bouncing/shaking can throw a die out.
        fun wall(sx: Float, sz: Float, px: Float, pz: Float) {
            val piece = GLCube(this, color = wallColor).apply {
                scale.apply { x = sx; y = roomHeight; z = sz }
                pos.apply { x = px; y = roomHeight / 2f; z = pz }
                isStatic = true
                setDirty()
            }
            scene.addChild(piece)
            roomPieces.add(piece)
            roomSurfaces.add(RoomSurface(piece, WALL_SATURATION, WALL_VALUE))
        }
        wall(wallLengthX, wallThickness, 0f, wallCenterOffsetZ)  // north
        wall(wallLengthX, wallThickness, 0f, -wallCenterOffsetZ) // south
        wall(wallThickness, wallLengthZ, wallCenterOffsetX, 0f)  // east
        wall(wallThickness, wallLengthZ, -wallCenterOffsetX, 0f) // west

        renderView.setRoomFootprintHalfExtents(roomHalfExtentX + wallThickness, roomHalfExtentZ + wallThickness)
    }

    /** Degrees around the hue wheel the room's surfaces should currently sit at - drifts slowly, full circle every [ROOM_COLOR_CYCLE_MILLIS]. */
    private fun currentRoomHueDegrees(): Float {
        val phase = (System.currentTimeMillis() % ROOM_COLOR_CYCLE_MILLIS) / ROOM_COLOR_CYCLE_MILLIS.toFloat()
        return phase * 360f
    }

    /**
     * Re-tints every room surface to the current hue, keeping each one's own
     * saturation/value (so the floor/walls/ceiling keep their relative brightness) -
     * called on a timer (see colorCycleTick) for a slow ambient color drift.
     * Must run on the GL thread.
     */
    private fun recolorRoomSurfacesOnGlThread() {
        if (roomSurfaces.isEmpty()) return
        val hue = currentRoomHueDegrees()
        for (surface in roomSurfaces) {
            surface.piece.setColor(hsvColor(hue, surface.saturation, surface.value))
        }
    }

    /** A deliberately desaturated, moderate-brightness color, so the room reads as subtle ambient tinting rather than a bright/signal color. */
    private fun hsvColor(hueDegrees: Float, saturation: Float, value: Float): Vec4 {
        val argb = Color.HSVToColor(floatArrayOf(hueDegrees, saturation, value))
        return Vec4(
            Color.red(argb) / 255f,
            Color.green(argb) / 255f,
            Color.blue(argb) / 255f,
            1f
        )
    }

    /**
     * Clears every die currently on the field, shrinks the room back to its base size,
     * and puts a single fresh starting die back. Also clears the roll history, since it's
     * a record of this session's throws and a reset starts a new one.
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun resetDiceOnGlThread() {
        val scene = renderView.scene
        for (entry in diceEntries) {
            scene.removeChild(entry.die)
        }
        diceEntries.clear()
        rebuildRoomOnGlThread(scene, diceCount = 0)
        restSinceMillis = 0L
        pendingRollCapture = true
        runOnUiThread {
            diceColorRow.rebuild(emptyList())
            rollHistoryOverlay.clear()
        }

        addDieOnGlThread(Vec3(0f, 1.5f, 0f))
    }

    /**
     * Adds one more die, dropped in from above at [position], growing the room first
     * if this die needs more space than it currently has (see targetRoomFloorArea).
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun addDieOnGlThread(position: Vec3) {
        val scene = renderView.scene
        if (targetRoomFloorArea(diceEntries.size + 1) != roomFloorArea) {
            rebuildRoomOnGlThread(scene, diceEntries.size + 1)
        }

        val margin = wallThickness + dieScale
        val clamped = Vec3(
            position.x.coerceIn(-roomHalfExtentX + margin, roomHalfExtentX - margin),
            position.y,
            position.z.coerceIn(-roomHalfExtentZ + margin, roomHalfExtentZ - margin)
        )
        val defaultColor = Vec4(0.95f, 0.93f, 0.85f, 1f)
        val die = GLCube(this, color = defaultColor, isDie = true).apply {
            scale.apply { x = dieScale; y = dieScale; z = dieScale }
            pos.apply { x = clamped.x; y = clamped.y; z = clamped.z }
            eulerRotDeg.assign(Vec3(Random.nextFloat() * 360f, Random.nextFloat() * 360f, Random.nextFloat() * 360f))
            restitution = 0.3f
            friction = 0.5f
            setDirty()
        }
        scene.addChild(die)
        val entry = DieEntry(die, vec4ToArgb(defaultColor))
        diceEntries.add(0, entry)
        restSinceMillis = 0L
        pendingRollCapture = true
        runOnUiThread { diceColorRow.addDieAtStart(entry) }
    }

    /**
     * Polled once per frame (see setOnPhysicsSteppedListener in onCreate): once every die
     * has been still for [REST_STABLE_MILLIS], reads off each one's face value (or null if
     * it isn't resting level on a single face) and records it as a roll-history entry.
     * [pendingRollCapture] makes this fire only once per period of rest, not every frame
     * while the dice keep sitting still - it's re-armed as soon as anything moves again.
     * Must run on the GL thread (it's a physics/renderer callback already on it).
     */
    private fun checkRollSettledOnGlThread() {
        if (!rollHistoryEnabled || diceEntries.isEmpty()) {
            restSinceMillis = 0L
            pendingRollCapture = true
            return
        }
        val atRest = renderView.scene.allChildrenAtRest(REST_LINEAR_SPEED, REST_ANGULAR_SPEED)
        if (!atRest) {
            restSinceMillis = 0L
            pendingRollCapture = true
            return
        }
        val now = System.currentTimeMillis()
        if (restSinceMillis == 0L) restSinceMillis = now
        if (!pendingRollCapture || now - restSinceMillis < REST_STABLE_MILLIS) return
        pendingRollCapture = false

        val results = diceEntries.map { entry ->
            DieResult(entry.die.upFaceValueOrNull(), entry.colorArgb)
        }
        val entry = RollEntry(now, results)
        runOnUiThread { rollHistoryOverlay.addEntry(entry) }
    }

    private fun randomSpawnPosition(): Vec3 {
        val margin = wallThickness + dieScale
        val rangeX = roomHalfExtentX - margin
        val rangeZ = roomHalfExtentZ - margin
        return Vec3(
            Random.nextFloat() * 2f * rangeX - rangeX,
            2f,
            Random.nextFloat() * 2f * rangeZ - rangeZ
        )
    }

    companion object {
        // Purely feel-tuning knobs - see the handleShakeReading doc comment.
        private const val SHAKE_DEADZONE = 3.5f // m/s^2
        private const val MIN_SHAKE_SENSITIVITY = 1.5f
        private const val MAX_SHAKE_SENSITIVITY = 7f
        private const val DEFAULT_SHAKE_SENSITIVITY = 3.5f

        // Also purely feel-tuning knobs - see targetRoomFloorArea. The room (and the
        // camera/light framing it - GLRenderer.recomputeFraming) starts sized for
        // BASE_DICE_COUNT dice and its "working volume" grows by ROOM_VOLUME_GROWTH_PER_DIE
        // for each one beyond that, capped at MAX_ROOM_HALF_EXTENT so it can't grow
        // without bound.
        private const val BASE_DICE_COUNT = 1
        private const val BASE_ROOM_HALF_EXTENT = 1.2f
        private const val ROOM_VOLUME_GROWTH_PER_DIE = 1.5f
        private const val MAX_ROOM_HALF_EXTENT = 3f

        // The room's ambient color: hue drifts slowly through the full color wheel
        // (see currentRoomHueDegrees/recolorRoomSurfacesOnGlThread), but saturation and
        // value are kept low/moderate per surface so it always reads as a subtle tint
        // rather than a bright, attention-grabbing color.
        private const val ROOM_COLOR_CYCLE_MILLIS = 60_000L
        private const val ROOM_COLOR_UPDATE_INTERVAL_MILLIS = 100L
        private const val FLOOR_SATURATION = 0.28f
        private const val FLOOR_VALUE = 0.60f
        private const val WALL_SATURATION = 0.30f
        private const val WALL_VALUE = 0.42f
        private const val CEILING_SATURATION = 0.32f
        private const val CEILING_VALUE = 0.32f

        // Roll-history rest detection - see checkRollSettledOnGlThread. Thresholds are
        // deliberately a bit loose (not exactly zero) since the contact solver leaves a
        // small amount of residual jitter even once a die has visibly stopped.
        private const val REST_LINEAR_SPEED = 0.08f // m/s
        private const val REST_ANGULAR_SPEED = 0.15f // rad/s
        private const val REST_STABLE_MILLIS = 450L
    }
}
