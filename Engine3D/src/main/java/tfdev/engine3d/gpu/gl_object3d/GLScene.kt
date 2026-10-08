package tfdev.engine3d.gpu.gl_object3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.LightSource
import tfdev.engine3d.gpu.physics.ContactSolver
import java.nio.FloatBuffer

class GLScene : GLObject3D() {
    override val openGLProgram: Int?
        get() = null
    override val vertexBuffer: FloatBuffer?
        get() = null
    override val vertexCount: Int
        get() = 0
    // Centered directly over the playing field, looking straight down; see
    // setLightHeight for how high up - GLRenderer keeps it in step with the camera,
    // both sitting just below the room's ceiling.
    override val lightSource = LightSource(Vec3(0f, 1f, 0f), Vec4(1f, 1f, 1f, 1f))

    /**
     * Moves the (centered, downward-looking) light straight up/down - GLRenderer
     * calls this to keep it in step with the camera, both just below the ceiling.
     */
    fun setLightHeight(y: Float) {
        lightSource.setPosition(Vec3(0f, y, 0f))
    }

    init {
        // The scene root is a container, not a physics body - without this it would
        // silently free-fall under its own default gravity, dragging every child's
        // *world* position down with it (while each child's own, parent-relative
        // `pos` stayed put) and corrupting every world-space collision computation.
        isStatic = true
    }

    /**
     * An extra, scene-wide acceleration (in addition to each object's own
     * [DynamicTransform.gravity][tfdev.engine3d.gpu.DynamicTransform]) applied to
     * every non-static top-level object every substep - e.g. to let a device's
     * accelerometer "tilt" the whole playing field.
     */
    val externalAcceleration = Vec3()

    /**
     * Adds [externalAcceleration] as a force to every non-static (top-level) object,
     * plus a matching torque so tilting doesn't just slide objects around but also
     * tips/rolls them over - the direction a ball would roll if gravity's lateral
     * component pointed along [externalAcceleration] (rotate it 90 degrees about Y).
     */
    fun applyExternalAcceleration() {
        if (externalAcceleration.isZero()) return
        val torque = Vec3(-externalAcceleration.z, 0f, externalAcceleration.x) * EXTERNAL_TORQUE_FACTOR
        for (child in children) {
            if (!child.isStatic) {
                child.force += externalAcceleration * child.mass
                child.torque += torque * child.mass
            }
        }
    }

    companion object {
        // Scales the rolling torque relative to the linear tilt force; purely a feel
        // tuning knob, not derived from anything physical like a dice's actual radius.
        private const val EXTERNAL_TORQUE_FACTOR = 0.25f
    }

    /**
     * Detects and resolves collisions between every pair of (top-level)
     * objects in the scene. Must be called after [updateSelfAndChild] so
     * that each object's `modelMatrix` reflects this frame's [step].
     */
    fun checkCollisions() {
        if (children.size < 2) return
        val pairs = ArrayList<Pair<GLObject3D, GLObject3D>>()
        for (i in children.indices) {
            for (j in i + 1 until children.size) {
                pairs.add(Pair(children[i], children[j]))
            }
        }
        ContactSolver.solve(pairs)
    }
}