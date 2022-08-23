package tfdev.engine3d.gpu


import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.Camera
import tfdev.engine3d.cpu.object3d.Scene
import tfdev.engine3d.gpu.gl_object3d.GLCube
import tfdev.engine3d.gpu.gl_object3d.GLScene
import tfdev.engine3d.gpu.gl_object3d.GLSquare
import tfdev.engine3d.gpu.gl_object3d.GLTriangle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(private val context: Context) : GLSurfaceView.Renderer {
    lateinit var scene: GLScene
    val camera = Camera(center = Vec3(0f, 0.5f, 0f), eye = Vec3(0f, 1.25f, -2f), up = Vec3(0f, 1f, 0f))
    val projectionMatrix4x4: Matrix4x4 = Matrix4x4()
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        scene = GLScene().apply { addChild(GLCube(context)) }
    }

    override fun onDrawFrame(unused: GL10) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        scene.updateSelfAndChild()
        scene.draw(projectionMatrix4x4 * camera.lookAtMatrix)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        Matrix4x4.project(projectionMatrix4x4,90f, ratio, 0.1f, 5f )
    }

}