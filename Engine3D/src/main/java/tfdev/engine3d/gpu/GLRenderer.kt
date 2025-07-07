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

    val camera =
        Camera(center = Vec3(0f, 0.5f, 0f), eye = Vec3(0f, 1.25f, -2f), up = Vec3(0f, 1f, 0f))
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
        val deltaTime = now - lastTime
        lastTime = now
        
        // Multiple collision checks per frame for better accuracy
        val numSubSteps = 3
        val subStepTime = deltaTime / numSubSteps
        
        for (i in 0 until numSubSteps) {
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

        Matrix4x4.project(projectionMatrix4x4, 90f, ratio, 0.1f, 5f)
    }

}