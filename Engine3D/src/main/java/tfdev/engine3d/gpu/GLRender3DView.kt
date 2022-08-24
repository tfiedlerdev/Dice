package tfdev.engine3d.gpu

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class GLRender3DView(context: Context): GLSurfaceView(context) {
    private val renderer: GLRenderer
    var enableYRotation = true
    init {

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(3)

        renderer = GLRenderer(getContext())

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)

        renderMode = RENDERMODE_WHEN_DIRTY
    }
    private var lastEventX = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (enableYRotation) {
            if (event != null) {
                if (event.action == MotionEvent.ACTION_MOVE) {
                    val x = event.getAxisValue(MotionEvent.AXIS_X)
                    val diff = lastEventX - x
                    lastEventX = x
                    renderer.camera.rotateY(diff / 5f)
                    requestRender()
                }
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastEventX = event.getAxisValue(MotionEvent.AXIS_X)
                }
            }
        }
        return enableYRotation
    }

    companion object{
    }
}