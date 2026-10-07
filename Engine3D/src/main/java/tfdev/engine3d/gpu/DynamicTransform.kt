package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform

/**
 * A [Transform] with rigid-body state: linear/angular velocity, the forces
 * driving them, and the material properties ([mass], [restitution],
 * [friction]) collision resolution needs.
 *
 * Integration uses semi-implicit ("symplectic") Euler: velocities are
 * updated from forces first, then position/orientation are integrated using
 * the *new* velocities. This is cheap (unlike RK4) and, crucially,
 * unconditionally stable for the stiff, fast-changing forces a contact
 * solver produces - explicit Euler tends to visibly gain energy on exactly
 * that kind of input.
 */
open class DynamicTransform(
    var mass: Float = 1f,
    val gravity: Vec3 = Vec3(0f, -9.81f, 0f)
) : Transform() {

    val velocity = Vec3()
    val force = Vec3()

    /** World-space angular velocity (rad/s). */
    val omega = Vec3()

    /** World-space torque accumulator, cleared every [stepPhysics]. */
    val torque = Vec3()

    /** World-space inverse inertia tensor for the *current* orientation; see [updateWorldInverseInertia]. */
    val Iinv = Matrix4x4()

    /** Bounciness: 0 = fully inelastic, 1 = fully elastic. */
    var restitution = 0.3f

    /** Coulomb friction coefficient used for both static and dynamic friction. */
    var friction = 0.6f

    /** Fraction of linear velocity removed per second (simple velocity damping, not air drag). */
    var linearDamping = 0.01f

    /** Fraction of angular velocity removed per second. */
    var angularDamping = 0.01f

    /**
     * Static bodies (floors, walls, ...) never move and have infinite mass
     * and inertia from the solver's point of view; they still take part in
     * collision detection as an immovable obstacle.
     */
    var isStatic = false
        set(value) {
            field = value
            if (value) {
                velocity.zeros()
                omega.zeros()
                force.zeros()
                torque.zeros()
            }
        }

    val invMass: Float get() = if (isStatic || mass <= 0f) 0f else 1f / mass

    private var stepsSinceOrthonormalize = 0

    /**
     * Inverse body-space inertia tensor diagonal of a solid box with the
     * object's *current* [scale] (full side lengths) and [mass]. Recomputed
     * every step (rather than cached once) so that resizing/re-massing an
     * object, or simply having set them after construction, behaves
     * correctly - a box's resistance to spinning genuinely depends on its
     * current dimensions.
     */
    private fun invBodyInertiaDiagonal(): Vec3 {
        if (isStatic || mass <= 0f) return Vec3(0f, 0f, 0f)
        val sx = scale.x
        val sy = scale.y
        val sz = scale.z
        val k = mass / 12f
        val ixx = k * (sy * sy + sz * sz)
        val iyy = k * (sx * sx + sz * sz)
        val izz = k * (sx * sx + sy * sy)
        return Vec3(
            if (ixx > 1e-8f) 1f / ixx else 0f,
            if (iyy > 1e-8f) 1f / iyy else 0f,
            if (izz > 1e-8f) 1f / izz else 0f
        )
    }

    /** Updates [Iinv] = R * Ibody^-1 * R^T for the body's current orientation. */
    private fun updateWorldInverseInertia() {
        val invBody = invBodyInertiaDiagonal()
        val invBodyMatrix = Matrix4x4(
            floatArrayOf(invBody.x, 0f, 0f, 0f),
            floatArrayOf(0f, invBody.y, 0f, 0f),
            floatArrayOf(0f, 0f, invBody.z, 0f),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        Iinv.assign(R * invBodyMatrix * R.transposeClone())
    }

    /**
     * Integrates forces/torques into velocities, then velocities into
     * position/orientation. Returns whether the body actually moved (used
     * to decide whether the transform needs to be marked dirty).
     */
    protected fun stepPhysics(deltaTimeMillis: Long): Boolean {
        if (isStatic) return false
        val dt = deltaTimeMillis.toFloat() / 1000f
        if (dt <= 0f) return false

        velocity += (gravity + force / mass) * dt
        velocity *= (1f - linearDamping * dt).coerceIn(0f, 1f)
        val deltaPos = velocity * dt
        pos += deltaPos

        updateWorldInverseInertia()
        val angularAcceleration = (Iinv * Vec4(torque.x, torque.y, torque.z, 0f)).xyz
        omega += angularAcceleration * dt
        omega *= (1f - angularDamping * dt).coerceIn(0f, 1f)

        val omegaLength = omega.length()
        if (omegaLength > 1e-6f) {
            val rotation = Matrix4x4.rotateAxisAngle(omegaLength * dt, omega / omegaLength)
            R.assign(rotation * R)
            // Repeatedly multiplying R by small rotation steps slowly accumulates floating
            // point drift until it stops being orthonormal; periodically clean it up.
            if (++stepsSinceOrthonormalize > 30) {
                R.orthonormalize()
                stepsSinceOrthonormalize = 0
            }
        }

        force.zeros()
        torque.zeros()

        return !deltaPos.isZero() || omegaLength > 1e-6f
    }

    /** World-space velocity of the material point currently at [worldPoint] (linear + omega x r). */
    fun velocityAtPoint(worldPoint: Vec3): Vec3 {
        val r = worldPoint - worldPosition()
        return velocity + omega.cross(r)
    }

    /** Applies an instantaneous impulse [impulse] (N*s) at [worldPoint], affecting both linear and angular velocity. */
    fun applyImpulse(impulse: Vec3, worldPoint: Vec3) {
        if (isStatic) return
        velocity += impulse * invMass
        val r = worldPoint - worldPosition()
        val angularImpulse = r.cross(impulse)
        omega += (Iinv * Vec4(angularImpulse.x, angularImpulse.y, angularImpulse.z, 0f)).xyz
    }

    /** Directly displaces the body by [correction] without touching velocity (used for penetration correction). */
    fun correctPosition(correction: Vec3) {
        if (isStatic) return
        pos += correction
    }
}
