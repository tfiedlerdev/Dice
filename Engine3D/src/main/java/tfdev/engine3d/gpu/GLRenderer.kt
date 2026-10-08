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
import kotlin.math.min
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
    // up pointing "towards the top of the screen"; -Z was picked arbitrarily. The eye
    // height itself is computed from the actual screen shape - see recomputeFraming.
    val camera =
        Camera(center = Vec3(0f, 0f, 0f), eye = Vec3(0f, MAX_EYE_DISTANCE, 0f), up = Vec3(0f, 0f, -1f))
    private val projectionMatrix4x4: Matrix4x4 = Matrix4x4()

    /**
     * Half the side length of the square floor the camera should always keep
     * entirely in view - set this (before the first frame, e.g. right after
     * constructing the view) to whatever the host's room footprint actually is.
     * Changing it later re-frames immediately rather than waiting for the surface
     * to resize again.
     */
    var roomFootprintHalfExtent = 2f
        set(value) {
            field = value
            recomputeFraming()
        }

    private var viewportAspect = 1f
    private var eyeDistance = MAX_EYE_DISTANCE

    /**
     * World-space "which way is up" direction, as reported by a device's gravity
     * sensor (see the host Activity) - i.e. the *slow* component of how the device
     * is being held, with any brief shake/jitter already filtered out by the sensor
     * itself. Read every frame by [updateCameraTilt]; writing it is the only thing
     * the host needs to do to drive the camera tilt.
     */
    var gravityUpDirection = Vec3(0f, 1f, 0f)
    private val smoothedUpDirection = Vec3(0f, 1f, 0f)

    /**
     * When false, the camera eases back to looking straight down (the same smooth,
     * asymptotic easing as a real tilt, just towards a fixed target) and ignores
     * [gravityUpDirection] until re-enabled, rather than snapping instantly either way.
     */
    var tiltEnabled = true

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
        // instead of ever visibly snapping to it - including the target itself
        // changing when tilt is toggled off/on.
        val target = if (tiltEnabled) gravityUpDirection else NEUTRAL_UP_DIRECTION
        val t = (1f - exp(-TILT_EASING_RATE * dt)).coerceIn(0f, 1f)
        smoothedUpDirection.x += (target.x - smoothedUpDirection.x) * t
        smoothedUpDirection.y += (target.y - smoothedUpDirection.y) * t
        smoothedUpDirection.z += (target.z - smoothedUpDirection.z) * t

        camera.setEyeDirection(cappedTiltDirection(), eyeDistance)
    }

    private fun cappedTiltDirection(): Vec3 {
        val horizontalLength = sqrt(
            smoothedUpDirection.x * smoothedUpDirection.x + smoothedUpDirection.z * smoothedUpDirection.z
        )
        val verticalComponent = smoothedUpDirection.y.coerceAtLeast(0.3f)
        val maxHorizontal = MAX_TILT_TAN * verticalComponent
        return if (horizontalLength > maxHorizontal && horizontalLength > 1e-5f) {
            val scale = maxHorizontal / horizontalLength
            Vec3(smoothedUpDirection.x * scale, verticalComponent, smoothedUpDirection.z * scale)
        } else {
            Vec3(smoothedUpDirection.x, verticalComponent, smoothedUpDirection.z)
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        viewportAspect = width.toFloat() / height.toFloat()
        recomputeFraming()
    }

    /**
     * Picks an eye (and light - see [GLScene.setLightHeight]) height such that the
     * room's square floor footprint always fits on screen, on *any* screen shape.
     *
     * A fixed eye height tuned by eye on one device is exactly as tall as it needs to
     * be for whatever aspect ratio that device happens to have, and too low (cropping
     * the room) on a narrower/taller one: the vertical field of view is a constant
     * ([FOV_Y_DEGREES]), but the *horizontal* one shrinks with the aspect ratio
     * (`tan(fovX/2) = aspect * tan(fovY/2)`) - a tall phone screen sees much less
     * side-to-side than top-to-bottom at a given height. So the camera has to sit
     * high enough to satisfy whichever of the two is more restrictive, which on a
     * typical portrait phone is the horizontal one.
     */
    private fun recomputeFraming() {
        val tanHalfFovY = tan(Math.toRadians(FOV_Y_DEGREES / 2.0)).toFloat()
        val tanHalfFovX = viewportAspect * tanHalfFovY
        val limitingTanHalfFov = min(tanHalfFovX, tanHalfFovY).coerceAtLeast(0.01f)

        eyeDistance = ((roomFootprintHalfExtent * FOOTPRINT_MARGIN) / limitingTanHalfFov)
            .coerceAtMost(MAX_EYE_DISTANCE)

        camera.setEyeDirection(cappedTiltDirection(), eyeDistance)
        scene.setLightHeight(eyeDistance)
        Matrix4x4.project(projectionMatrix4x4, FOV_Y_DEGREES, viewportAspect, 0.1f, eyeDistance + FAR_PLANE_MARGIN)
    }

    companion object {
        private const val PHYSICS_SUBSTEPS = 4
        private const val MAX_FRAME_TIME_MILLIS = 100L

        private const val FOV_Y_DEGREES = 90f

        /** Extra headroom beyond the exact footprint, so the walls aren't right at the frame's edge. */
        private const val FOOTPRINT_MARGIN = 1.15f

        /**
         * However extreme the aspect ratio (or large the host's room footprint, if it
         * grows with e.g. the number of dice on the field), the eye - and the room's
         * ceiling, which the host must keep above this - never need to exceed this.
         */
        const val MAX_EYE_DISTANCE = 10f

        private const val FAR_PLANE_MARGIN = 2f

        /** Higher = the camera catches up to a changed tilt faster. */
        private const val TILT_EASING_RATE = 3f

        /** tan(22 degrees): the camera can lean this far off straight-down at most, however hard the device is tilted. */
        private val MAX_TILT_TAN = tan(Math.toRadians(22.0)).toFloat()

        private val NEUTRAL_UP_DIRECTION = Vec3(0f, 1f, 0f)
    }
}
