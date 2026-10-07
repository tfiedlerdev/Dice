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
    override val lightSource = LightSource(
        Vec3(-0.2f, 1f, 0f), Vec3(0.75f, 1.5f, 0f),
        Vec4(1f, 1f, 1f, 1f)
    )

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

    /** Adds [externalAcceleration] as a force to every non-static (top-level) object. */
    fun applyExternalAcceleration() {
        if (externalAcceleration.isZero()) return
        for (child in children) {
            if (!child.isStatic) {
                child.force += externalAcceleration * child.mass
            }
        }
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