package tfdev.engine3d.gpu


import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.Camera
import tfdev.engine3d.gpu.gl_object3d.GLScene
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tan

class GLRenderer(
    private val context: Context,
    private val scene: GLScene
) : GLSurfaceView.Renderer {
    private var isSurfaceCreated = false

    var onSceneInitialized: ((scene: GLScene) -> Unit)? = null
        set(value) {
            if(isSurfaceCreated) value?.invoke(scene)
            field = value
        }

    // Looking straight down at the playing field from above, from just below the
    // room's ceiling. `up` can't be the vertical (0,1,0) here - it has to be some
    // direction orthogonal to the (also vertical) view direction, which is what ends
    // up pointing "towards the top of the screen"; -Z was picked arbitrarily.
    val camera =
        Camera(center = Vec3(0f, 0f, 0f), eye = Vec3(0f, EYE_DISTANCE, 0f), up = Vec3(0f, 0f, -1f))
    private val projectionMatrix4x4: Matrix4x4 = Matrix4x4()

    /**
     * World-space "which way is up" direction, as reported by a device's gravity
     * sensor (see the host Activity) - i.e. the *slow* component of how the device
     * is being held, with any brief shake/jitter already filtered out by the sensor
     * itself. Read every frame by [updateCameraTilt]; writing it is the only thing
     * the host needs to do to drive the camera tilt.
     */
    var gravityUpDirection = Vec3(0f, 1f, 0f)
    private val smoothedUpDirection = Vec3(0f, 1f, 0f)

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LESS)
        isSurfaceCreated = true

        onSceneInitialized?.invoke(scene)
    }

    private var lastTime = System.currentTimeMillis()
    override fun onDrawFrame(unused: GL10) {
        // Redraw background color
        val now = System.currentTimeMillis()
        // Clamp so a hitch (e.g. the app being paused and resumed) can't produce one huge,
        // tunneling-prone physics step; instead run several smaller, more accurate substeps.
        val deltaTime = (now - lastTime).coerceIn(0L, MAX_FRAME_TIME_MILLIS)
        lastTime = now

        updateCameraTilt(deltaTime)

        val subStepTime = deltaTime / PHYSICS_SUBSTEPS
        repeat(PHYSICS_SUBSTEPS) {
            scene.applyExternalAcceleration()
            scene.step(subStepTime)
            scene.updateSelfAndChild()
            scene.checkCollisions()
        }

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        scene.draw(projectionMatrix4x4 * camera.lookAtMatrix)

    }

    /**
     * Eases the camera towards [gravityUpDirection] rather than snapping straight to
     * it, and caps how far from straight-down it can ever lean (both per the user's
     * request: a slow tilt should subtly lean the view, not jerk it around, and
     * never so far that the room stops reading as "looking down into it").
     */
    private fun updateCameraTilt(deltaTimeMillis: Long) {
        val dt = deltaTimeMillis.toFloat() / 1000f
        if (dt <= 0f) return

        // Exponential/asymptotic approach: each frame covers the same *fraction* of
        // whatever gap remains, so it slows down smoothly as it nears the target
        // instead of ever visibly snapping to it.
        val t = (1f - exp(-TILT_EASING_RATE * dt)).coerceIn(0f, 1f)
        smoothedUpDirection.x += (gravityUpDirection.x - smoothedUpDirection.x) * t
        smoothedUpDirection.y += (gravityUpDirection.y - smoothedUpDirection.y) * t
        smoothedUpDirection.z += (gravityUpDirection.z - smoothedUpDirection.z) * t

        val horizontalLength = sqrt(
            smoothedUpDirection.x * smoothedUpDirection.x + smoothedUpDirection.z * smoothedUpDirection.z
        )
        val verticalComponent = smoothedUpDirection.y.coerceAtLeast(0.3f)
        val maxHorizontal = MAX_TILT_TAN * verticalComponent
        val direction = if (horizontalLength > maxHorizontal && horizontalLength > 1e-5f) {
            val scale = maxHorizontal / horizontalLength
            Vec3(smoothedUpDirection.x * scale, verticalComponent, smoothedUpDirection.z * scale)
        } else {
            Vec3(smoothedUpDirection.x, verticalComponent, smoothedUpDirection.z)
        }
        camera.setEyeDirection(direction, EYE_DISTANCE)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        Matrix4x4.project(projectionMatrix4x4, 90f, ratio, 0.1f, 10f)
    }

    companion object {
        private const val PHYSICS_SUBSTEPS = 4
        private const val MAX_FRAME_TIME_MILLIS = 100L

        /** How far below the room's ceiling the camera (and the light) sit. */
        const val EYE_DISTANCE = 2.65f

        /** Higher = the camera catches up to a changed tilt faster. */
        private const val TILT_EASING_RATE = 3f

        /** tan(22 degrees): the camera can lean this far off straight-down at most, however hard the device is tilted. */
        private val MAX_TILT_TAN = tan(Math.toRadians(22.0)).toFloat()
    }
}
