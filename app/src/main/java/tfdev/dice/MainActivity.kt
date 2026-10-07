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
import tfdev.engine3d.gpu.gl_object3d.GLCube
import tfdev.engine3d.gpu.gl_object3d.GLObject3D
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var renderView: GLRender3DView
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val dice = mutableListOf<GLObject3D>()
    private val dieScale = 0.5f

    // Half the floor's walkable footprint - used both to build the room and to
    // keep newly-added dice from spawning inside/outside a wall.
    private val roomHalfExtent = 3f
    private val wallHeight = 1f
    private val wallThickness = 0.2f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        renderView = findViewById(R.id.renderView_activityMain)
        // The camera looks straight down and never orbits, so dragging on the
        // view has nothing to rotate.
        renderView.enableYRotation = false

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
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // This callback is invoked from onSurfaceCreated, i.e. already on the GL thread.
        renderView.setOnSceneInitializedListener { scene ->
            buildRoom(scene)
            resetDiceOnGlThread()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    /**
     * Treats the device's accelerometer reading as if the whole playing field were
     * tilted by that amount: holding the phone flat and tilting it left/right or
     * forward/back pushes the dice around exactly as if they sat in a real tilted
     * tray. Depending on how you hold the phone relative to the on-screen top-down
     * view, you may need to flip a sign below to match - the physics itself
     * (applying the raw reading as a lateral acceleration) is correct either way.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val scene = renderView.scene
        scene.externalAcceleration.x = event.values[0]
        scene.externalAcceleration.z = -event.values[1]
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

        val wallColor = Vec4(0.35f, 0.38f, 0.45f, 1f)
        val wallCenterOffset = roomHalfExtent + wallThickness / 2f
        val wallLength = roomHalfExtent * 2f + wallThickness * 2f

        fun wall(sx: Float, sz: Float, px: Float, pz: Float) {
            scene.addChild(GLCube(this, color = wallColor).apply {
                scale.apply { x = sx; y = wallHeight; z = sz }
                pos.apply { x = px; y = wallHeight / 2f; z = pz }
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
        val die = GLCube(this).apply {
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
}
