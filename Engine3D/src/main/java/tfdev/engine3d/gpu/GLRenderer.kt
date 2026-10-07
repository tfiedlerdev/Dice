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

    // Looking straight down at the playing field from above. `up` can't be the
    // vertical (0,1,0) here - it has to be some direction orthogonal to the (also
    // vertical) view direction, which is what ends up pointing "towards the top of
    // the screen"; -Z was picked arbitrarily.
    val camera =
        Camera(center = Vec3(0f, 0f, 0f), eye = Vec3(0f, 8f, 0f), up = Vec3(0f, 0f, -1f))
    private val projectionMatrix4x4: Matrix4x4 = Matrix4x4()


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

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        Matrix4x4.project(projectionMatrix4x4, 90f, ratio, 0.1f, 20f)
    }

    companion object {
        private const val PHYSICS_SUBSTEPS = 4
        private const val MAX_FRAME_TIME_MILLIS = 100L
    }
}