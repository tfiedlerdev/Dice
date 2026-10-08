package tfdev.dice

import androidx.appcompat.app.AppCompatActivity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
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
    private val dieScale = 0.5f

    // Half the floor's walkable footprint - used both to build the room and to
    // keep newly-added dice from spawning inside/outside a wall.
    private val roomHalfExtent = 2f
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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // This callback is invoked from onSurfaceCreated, i.e. already on the GL thread.
        renderView.setOnSceneInitializedListener { scene ->
            buildRoom(scene)
            resetDiceOnGlThread()
        }
    }

    override fun onResume() {
        super.onResume()
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
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
        scene.externalAcceleration.x = -worldAx * excess * SHAKE_SENSITIVITY
        scene.externalAcceleration.y = -worldAy * excess * SHAKE_SENSITIVITY
        scene.externalAcceleration.z = -worldAz * excess * SHAKE_SENSITIVITY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildRoom(scene: GLScene) {
        val floor = GLCube(this, color = Vec4(0.55f, 0.58f, 0.65f, 1f)).apply {
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

        val ceilingThickness = 0.2f
        val ceiling = GLCube(this, color = Vec4(0.3f, 0.32f, 0.38f, 1f)).apply {
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

        val wallColor = Vec4(0.35f, 0.38f, 0.45f, 1f)
        val wallCenterOffset = roomHalfExtent + wallThickness / 2f
        val wallLength = roomHalfExtent * 2f + wallThickness * 2f

        // Walls span the full floor-to-ceiling height, so the room is fully sealed -
        // no amount of bouncing/shaking can throw a die out.
        fun wall(sx: Float, sz: Float, px: Float, pz: Float) {
            scene.addChild(GLCube(this, color = wallColor).apply {
                scale.apply { x = sx; y = roomHeight; z = sz }
                pos.apply { x = px; y = roomHeight / 2f; z = pz }
                isStatic = true
                setDirty()
            })
        }
        wall(wallLength, wallThickness, 0f, wallCenterOffset)  // north
        wall(wallLength, wallThickness, 0f, -wallCenterOffset) // south
        wall(wallThickness, wallLength, wallCenterOffset, 0f)  // east
        wall(wallThickness, wallLength, -wallCenterOffset, 0f) // west
    }

    /**
     * Clears every die currently on the field and puts a fresh starting pair back.
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun resetDiceOnGlThread() {
        val scene = renderView.scene
        for (die in dice) {
            scene.removeChild(die)
        }
        dice.clear()

        addDieOnGlThread(Vec3(-0.6f, 1.5f, -0.4f))
        addDieOnGlThread(Vec3(0.6f, 2f, 0.5f))
    }

    /**
     * Adds one more die, dropped in from above at [position].
     * Must run on the GL thread (see the queueEvent calls at the call sites).
     */
    private fun addDieOnGlThread(position: Vec3) {
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
        renderView.scene.addChild(die)
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
        private const val SHAKE_SENSITIVITY = 1.2f
    }
}
