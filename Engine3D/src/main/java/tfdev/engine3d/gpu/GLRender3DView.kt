package tfdev.engine3d.gpu

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.gl_object3d.GLScene

class GLRender3DView : GLSurfaceView {
    val scene: GLScene
    private val renderer: GLRenderer
    var enableYRotation = true

    // Constructor for XML inflation
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        scene = GLScene()

        setEGLContextClientVersion(3)

        renderer = GLRenderer(context, scene)
        setRenderer(renderer)

        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // Constructor for programmatically creating the view
    constructor(context: Context, scene: GLScene) : super(context) {
        this.scene = scene

        setEGLContextClientVersion(3)

        renderer = GLRenderer(context, scene)
        setRenderer(renderer)

        renderMode = RENDERMODE_CONTINUOUSLY
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

    fun setOnSceneInitializedListener(listener:((scene: GLScene) -> Unit)) {
        renderer.onSceneInitialized = listener
    }

    /** See [GLRenderer.gravityUpDirection]. */
    fun setGravityUpDirection(direction: Vec3) {
        renderer.gravityUpDirection = direction
    }

    /** See [GLRenderer.roomFootprintHalfExtent]. */
    fun setRoomFootprintHalfExtent(halfExtent: Float) {
        renderer.roomFootprintHalfExtent = halfExtent
    }

    /** See [GLRenderer.tiltEnabled]. */
    fun setCameraTiltEnabled(enabled: Boolean) {
        renderer.tiltEnabled = enabled
    }

    /** See [GLRenderer.secondaryLightFollowsTilt]. */
    fun setSecondaryLightFollowsTilt(followsTilt: Boolean) {
        renderer.secondaryLightFollowsTilt = followsTilt
    }

    companion object{
    }
}