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
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
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

    private val dice = mutableListOf<GLObject3D>()
    private val roomPieces = mutableListOf<GLObject3D>()

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

    // Half the floor's walkable footprint - starts small and grows with the number of
    // dice on the field (see targetRoomHalfExtent/rebuildRoomOnGlThread) rather than
    // being one size that has to suit anywhere from 2 dice to a dozen. Used both to
    // build the room and to keep newly-added dice from spawning inside/outside a wall.
    private var roomHalfExtent = BASE_ROOM_HALF_EXTENT
    private val wallThickness = 0.2f

    // The camera (and light) height is computed from the screen's actual aspect ratio
    // so the room's footprint fits on screen on any device (see GLRenderer -
    // recomputeFraming), capped at MAX_EYE_DISTANCE - so the ceiling just needs to
    // clear that cap, not any one particular computed height.
    private val roomHeight = GLRenderer.MAX_EYE_DISTANCE + 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        renderView = findViewById(R.id.renderView_activityMain)
        // The camera looks straight down and never orbits, so dragging on the
        // view has nothing to rotate.
        renderView.enableYRotation = false
        renderView.setRoomFootprintHalfExtent(roomHalfExtent + wallThickness)

        // Button clicks land on the UI thread, but creating a die compiles/links an
        // OpenGL shader program, which - like every GL call - only works on the
        // GLSurfaceView's own GL thread; queueEvent hands the work over to it.
        findViewById<Button>(R.id.button_reset_activityMain).setOnClickListener {
            renderView.queueEvent { resetDiceOnGlThread() }
        }
        findViewById<Button>(R.id.button_addDie_activityMain).setOnClickListener {
            renderView.queueEvent { addDieOnGlThread(randomSpawnPosition()) }
        }

        findViewById<SeekBar>(R.id.seekBar_shakeSensitivity_activityMain).apply {
            progress = sensitivityToProgress(DEFAULT_SHAKE_SENSITIVITY)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    shakeSensitivity = progressToSensitivity(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<Switch>(R.id.switch_cameraTilt_activityMain)
            .setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                renderView.setCameraTiltEnabled(isChecked)
            }

        findViewById<Switch>(R.id.switch_pointLight_activityMain)
            .setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                renderView.scene.setPointLightEnabled(isChecked)
            }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // This callback is invoked from onSurfaceCreated, i.e. already on the GL thread.
        renderView.setOnSceneInitializedListener { scene ->
            rebuildRoomOnGlThread(scene, diceCount = 0)
            resetDiceOnGlThread()
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
     * How far out the room should extend to comfortably fit [diceCount] dice - grows
     * the room's *volume* (floor area times its fixed height) linearly with dice count,
     * rather than its floor area directly. Since the room is quite tall, a given volume
     * increase corresponds to a much smaller area (and half-extent) increase, so this
     * reads as noticeably gentler growth than scaling the area itself would.
     */
    private fun targetRoomHalfExtent(diceCount: Int): Float {
        val extraDice = (diceCount.coerceAtLeast(BASE_DICE_COUNT) - BASE_DICE_COUNT)
        val baseVolume = (BASE_ROOM_HALF_EXTENT * 2f) * (BASE_ROOM_HALF_EXTENT * 2f) * roomHeight
        val maxVolume = (MAX_ROOM_HALF_EXTENT * 2f) * (MAX_ROOM_HALF_EXTENT * 2f) * roomHeight
        val volume = (baseVolume + extraDice * ROOM_VOLUME_GROWTH_PER_DIE).coerceAtMost(maxVolume)
        val area = volume / roomHeight
        return sqrt(area) / 2f
    }

    /**
     * Rebuilds the floor/walls/ceiling sized to comfortably fit [diceCount] dice (never
     * smaller than the base size for [BASE_DICE_COUNT]) and reframes the camera/light to
     * match - so the field starts small and close, then grows as more dice join it,
     * rather than being one fixed size that has to suit both 2 dice and a dozen.
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun rebuildRoomOnGlThread(scene: GLScene, diceCount: Int) {
        for (piece in roomPieces) {
            scene.removeChild(piece)
        }
        roomPieces.clear()
        roomSurfaces.clear()

        roomHalfExtent = targetRoomHalfExtent(diceCount)
        val hue = currentRoomHueDegrees()

        val floor = GLCube(this, color = hsvColor(hue, FLOOR_SATURATION, FLOOR_VALUE)).apply {
            scale.apply {
                x = roomHalfExtent * 2f
                y = 0.2f
                z = roomHalfExtent * 2f
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
                x = roomHalfExtent * 2f
                y = ceilingThickness
                z = roomHalfExtent * 2f
            }
            pos.y = roomHeight + ceilingThickness / 2f
            isStatic = true
            setDirty()
        }
        scene.addChild(ceiling)
        roomPieces.add(ceiling)
        roomSurfaces.add(RoomSurface(ceiling, CEILING_SATURATION, CEILING_VALUE))

        val wallColor = hsvColor(hue, WALL_SATURATION, WALL_VALUE)
        val wallCenterOffset = roomHalfExtent + wallThickness / 2f
        val wallLength = roomHalfExtent * 2f + wallThickness * 2f

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
        wall(wallLength, wallThickness, 0f, wallCenterOffset)  // north
        wall(wallLength, wallThickness, 0f, -wallCenterOffset) // south
        wall(wallThickness, wallLength, wallCenterOffset, 0f)  // east
        wall(wallThickness, wallLength, -wallCenterOffset, 0f) // west

        renderView.setRoomFootprintHalfExtent(roomHalfExtent + wallThickness)
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
     * and puts a single fresh starting die back.
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun resetDiceOnGlThread() {
        val scene = renderView.scene
        for (die in dice) {
            scene.removeChild(die)
        }
        dice.clear()
        rebuildRoomOnGlThread(scene, diceCount = 0)

        addDieOnGlThread(Vec3(0f, 1.5f, 0f))
    }

    /**
     * Adds one more die, dropped in from above at [position], growing the room first
     * if this die needs more space than it currently has (see targetRoomHalfExtent).
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun addDieOnGlThread(position: Vec3) {
        val scene = renderView.scene
        if (targetRoomHalfExtent(dice.size + 1) != roomHalfExtent) {
            rebuildRoomOnGlThread(scene, dice.size + 1)
        }

        val margin = wallThickness + dieScale
        val clamped = Vec3(
            position.x.coerceIn(-roomHalfExtent + margin, roomHalfExtent - margin),
            position.y,
            position.z.coerceIn(-roomHalfExtent + margin, roomHalfExtent - margin)
        )
        val die = GLCube(this, color = Vec4(0.95f, 0.93f, 0.85f, 1f), isDie = true).apply {
            scale.apply { x = dieScale; y = dieScale; z = dieScale }
            pos.apply { x = clamped.x; y = clamped.y; z = clamped.z }
            eulerRotDeg.assign(Vec3(Random.nextFloat() * 360f, Random.nextFloat() * 360f, Random.nextFloat() * 360f))
            restitution = 0.3f
            friction = 0.5f
            setDirty()
        }
        scene.addChild(die)
        dice.add(die)
    }

    private fun randomSpawnPosition(): Vec3 {
        val margin = wallThickness + dieScale
        val range = roomHalfExtent - margin
        return Vec3(
            Random.nextFloat() * 2f * range - range,
            2f,
            Random.nextFloat() * 2f * range - range
        )
    }

    companion object {
        // Purely feel-tuning knobs - see the handleShakeReading doc comment.
        private const val SHAKE_DEADZONE = 3.5f // m/s^2
        private const val MIN_SHAKE_SENSITIVITY = 1f
        private const val MAX_SHAKE_SENSITIVITY = 6f
        private const val DEFAULT_SHAKE_SENSITIVITY = 2.5f

        // Also purely feel-tuning knobs - see targetRoomHalfExtent. The room (and the
        // camera/light framing it - GLRenderer.recomputeFraming) starts sized for
        // BASE_DICE_COUNT dice and its *volume* grows by ROOM_VOLUME_GROWTH_PER_DIE
        // for each one beyond that, capped at MAX_ROOM_HALF_EXTENT so it can't grow
        // without bound.
        private const val BASE_DICE_COUNT = 1
        private const val BASE_ROOM_HALF_EXTENT = 1.2f
        private const val ROOM_VOLUME_GROWTH_PER_DIE = 5f
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
    }
}
