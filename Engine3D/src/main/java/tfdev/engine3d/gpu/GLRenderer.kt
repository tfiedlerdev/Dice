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
import kotlin.math.max
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
     * Half the X/Z side lengths of the floor the camera should always keep entirely
     * in view - set these (before the first frame, e.g. right after constructing the
     * view) to whatever the host's room footprint actually is. Changing them later
     * re-frames immediately rather than waiting for the surface to resize again. The
     * two need not be equal - see [setRoomFootprintHalfExtents].
     */
    private var roomFootprintHalfExtentX = 2f
    private var roomFootprintHalfExtentZ = 2f

    /** See [roomFootprintHalfExtentX]/[roomFootprintHalfExtentZ]. */
    fun setRoomFootprintHalfExtents(halfExtentX: Float, halfExtentZ: Float) {
        roomFootprintHalfExtentX = halfExtentX
        roomFootprintHalfExtentZ = halfExtentZ
        recomputeFraming()
    }

    private var viewportAspect = 1f
    private var eyeDistance = MAX_EYE_DISTANCE

    // Recomputed in recomputeFraming from the room's actual current size - see
    // cappedTiltDirection. The fallback value (pre-first-frame) matches the old
    // fixed-angle behavior, not that it should ever actually be read that early.
    private var maxTiltTanX = MAX_TILT_TAN
    private var maxTiltTanZ = MAX_TILT_TAN

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

    /** Invoked once per frame, right after the physics step - e.g. to check whether the dice have come to rest. */
    var onPhysicsStepped: (() -> Unit)? = null

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
        onPhysicsStepped?.invoke()

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

    /**
     * Caps how far the camera can lean per axis - *not* just a single fixed angle,
     * since with a non-square room (see [setRoomFootprintHalfExtents]) a fixed angle
     * either wastes most of the narrower axis's headroom or, worse, leans the eye
     * laterally past that axis's own walls (visibly clipping through them). Each
     * axis's cap ([maxTiltTanX]/[maxTiltTanZ]) is instead derived in [recomputeFraming]
     * from that axis's actual room size, so the eye can never lean past
     * [SAFE_TILT_FRACTION] of the way to the wall, regardless of room shape.
     */
    private fun cappedTiltDirection(): Vec3 {
        val verticalComponent = smoothedUpDirection.y.coerceAtLeast(0.3f)
        val maxHorizontalX = (maxTiltTanX * verticalComponent).coerceAtLeast(1e-5f)
        val maxHorizontalZ = (maxTiltTanZ * verticalComponent).coerceAtLeast(1e-5f)

        // How far outside the (per-axis-scaled) unit circle the current lean sits -
        // i.e. an ellipse matching the room's own aspect, not a circle.
        val normalizedX = smoothedUpDirection.x / maxHorizontalX
        val normalizedZ = smoothedUpDirection.z / maxHorizontalZ
        val ellipseDistanceSq = normalizedX * normalizedX + normalizedZ * normalizedZ

        return if (ellipseDistanceSq > 1f) {
            val scale = 1f / sqrt(ellipseDistanceSq)
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
     * room's floor footprint always fits on screen, on *any* screen shape.
     *
     * A fixed eye height tuned by eye on one device is exactly as tall as it needs to
     * be for whatever aspect ratio that device happens to have, and too low (cropping
     * the room) on a narrower/taller one: the vertical field of view is a constant
     * ([FOV_Y_DEGREES]), but the *horizontal* one shrinks with the aspect ratio
     * (`tan(fovX/2) = aspect * tan(fovY/2)`) - a tall phone screen sees much less
     * side-to-side than top-to-bottom at a given height. The host is expected to size
     * [roomFootprintHalfExtentX]/[roomFootprintHalfExtentZ] to match the current
     * [viewportAspect] (see [setRoomFootprintHalfExtents]), in which case both axes
     * need the exact same eye distance - the max is taken only as a safety margin for
     * the brief moments (e.g. mid-rotation) where the host hasn't caught up yet.
     */
    private fun recomputeFraming() {
        val tanHalfFovY = tan(Math.toRadians(FOV_Y_DEGREES / 2.0)).toFloat()
        val tanHalfFovX = (viewportAspect * tanHalfFovY).coerceAtLeast(0.01f)

        val eyeDistanceForX = (roomFootprintHalfExtentX * FOOTPRINT_MARGIN) / tanHalfFovX
        val eyeDistanceForZ = (roomFootprintHalfExtentZ * FOOTPRINT_MARGIN) / tanHalfFovY.coerceAtLeast(0.01f)

        eyeDistance = max(eyeDistanceForX, eyeDistanceForZ).coerceAtMost(MAX_EYE_DISTANCE)

        // tan(leanAngle) * eyeDistance is (approximately) how far the eye moves
        // laterally per axis when fully tilted; capping that to SAFE_TILT_FRACTION of
        // the room's own half-extent on that axis keeps the eye from ever leaning far
        // enough to cross a wall, however extreme the room's aspect ratio is. Also
        // capped at MAX_TILT_TAN so a generously-sized room doesn't invite an
        // unnecessarily dramatic lean.
        maxTiltTanX = (SAFE_TILT_FRACTION * roomFootprintHalfExtentX / eyeDistance).coerceAtMost(MAX_TILT_TAN)
        maxTiltTanZ = (SAFE_TILT_FRACTION * roomFootprintHalfExtentZ / eyeDistance).coerceAtMost(MAX_TILT_TAN)

        camera.setEyeDirection(cappedTiltDirection(), eyeDistance)
        scene.setLightHeight(eyeDistance)
        Matrix4x4.project(projectionMatrix4x4, FOV_Y_DEGREES, viewportAspect, 0.1f, eyeDistance + FAR_PLANE_MARGIN)
    }

    companion object {
        private const val PHYSICS_SUBSTEPS = 4
        private const val MAX_FRAME_TIME_MILLIS = 100L

        private const val FOV_Y_DEGREES = 90f

        /** Extra headroom beyond the exact footprint, so the walls aren't right at the frame's edge. */
        private const val FOOTPRINT_MARGIN = 1.08f

        /**
         * However extreme the aspect ratio (or large the host's room footprint, if it
         * grows with e.g. the number of dice on the field), the eye - and the room's
         * ceiling, which the host must keep above this - never need to exceed this.
         */
        const val MAX_EYE_DISTANCE = 10f

        private const val FAR_PLANE_MARGIN = 2f

        /** Higher = the camera catches up to a changed tilt faster. */
        private const val TILT_EASING_RATE = 3f

        /** tan(22 degrees): the camera never leans further than this off straight-down, regardless of room size - see [cappedTiltDirection]. */
        private val MAX_TILT_TAN = tan(Math.toRadians(22.0)).toFloat()

        /** The eye's lean never closes more than this fraction of the gap to a wall - see [cappedTiltDirection]. */
        private const val SAFE_TILT_FRACTION = 0.8f

        private val NEUTRAL_UP_DIRECTION = Vec3(0f, 1f, 0f)
    }
}
