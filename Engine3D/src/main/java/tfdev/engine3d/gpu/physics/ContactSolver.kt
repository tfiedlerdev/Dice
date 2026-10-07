package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D
import kotlin.math.abs
import kotlin.math.max

/**
 * Resolves a set of body pairs that are potentially touching into actual
 * contact manifolds and then solves them with a sequential-impulse (Gauss-
 * Seidel) solver: several passes over every contact point, each pass
 * nudging the accumulated normal/friction impulse a little closer to the
 * value that would satisfy "don't interpenetrate, don't pass through each
 * other's surface" for every contact simultaneously. This converges fast
 * and - unlike resolving one contact fully before moving to the next - is
 * what lets e.g. a box resting on two contact points on the floor settle
 * instead of rocking between them.
 *
 * Restitution and friction are both applied per contact point; penetration
 * is corrected afterwards in a separate position-only pass so that pushing
 * bodies apart never injects extra energy into the velocity solve.
 */
object ContactSolver {
    private const val VELOCITY_ITERATIONS = 10
    private const val POSITION_ITERATIONS = 4

    /** Closing speed below which we treat a contact as inelastic, to stop slow resting contacts from micro-bouncing forever. */
    private const val RESTITUTION_VELOCITY_THRESHOLD = 0.5f

    /** How much of the remaining penetration to correct per position iteration. */
    private const val POSITION_CORRECTION_PERCENT = 0.6f

    /** Allowed resting penetration; correcting this last bit away is what causes jitter, so we don't. */
    private const val PENETRATION_SLOP = 0.005f

    private class PreparedContact(
        val bodyA: GLObject3D,
        val bodyB: GLObject3D,
        val point: Vec3,
        val normal: Vec3,
        val tangent1: Vec3,
        val tangent2: Vec3,
        val normalMass: Float,
        val tangent1Mass: Float,
        val tangent2Mass: Float,
        val restitutionBias: Float,
        var penetration: Float
    ) {
        var normalImpulse = 0f
        var tangent1Impulse = 0f
        var tangent2Impulse = 0f
    }

    /**
     * Detects and resolves collisions for every given (potentially
     * overlapping) pair of bodies. [dtSeconds] is currently unused by the
     * solver itself (impulses are instantaneous) but kept for future use
     * (e.g. speculative contacts) and symmetry with [GLObject3D.step].
     */
    fun solve(pairs: List<Pair<GLObject3D, GLObject3D>>) {
        val contacts = ArrayList<PreparedContact>()
        for ((a, b) in pairs) {
            if (a.isStatic && b.isStatic) continue
            val manifold = OBBCollision.detect(
                OBB.fromModelMatrix(a.modelMatrix),
                OBB.fromModelMatrix(b.modelMatrix)
            )
            for (contactPoint in manifold) {
                contacts.add(prepare(a, b, contactPoint))
            }
        }
        if (contacts.isEmpty()) return

        repeat(VELOCITY_ITERATIONS) {
            for (c in contacts) solveVelocity(c)
        }
        repeat(POSITION_ITERATIONS) {
            for (c in contacts) solvePosition(c)
        }
    }

    private fun prepare(a: GLObject3D, b: GLObject3D, contactPoint: ContactPoint): PreparedContact {
        val normal = contactPoint.normal
        val tangent1Raw = if (abs(normal.x) < 0.9f) Vec3(1f, 0f, 0f).cross(normal) else Vec3(0f, 1f, 0f).cross(normal)
        val tangent1 = tangent1Raw.normalize()
        val tangent2 = normal.cross(tangent1)

        val rA = contactPoint.worldPoint - a.worldPosition()
        val rB = contactPoint.worldPoint - b.worldPosition()

        val normalMass = effectiveMass(a, b, rA, rB, normal)
        val tangent1Mass = effectiveMass(a, b, rA, rB, tangent1)
        val tangent2Mass = effectiveMass(a, b, rA, rB, tangent2)

        val relativeVelocity = b.velocityAtPoint(contactPoint.worldPoint) - a.velocityAtPoint(contactPoint.worldPoint)
        val closingSpeed = relativeVelocity dot normal
        val restitution = max(a.restitution, b.restitution)
        val restitutionBias =
            if (closingSpeed < -RESTITUTION_VELOCITY_THRESHOLD) -restitution * closingSpeed else 0f

        return PreparedContact(
            a, b, contactPoint.worldPoint, normal, tangent1, tangent2,
            normalMass, tangent1Mass, tangent2Mass, restitutionBias, contactPoint.penetrationDepth
        )
    }

    /** 1 / (invMassA + invMassB + angular resistance of both bodies to rotating about [axis] at these contact arms). */
    private fun effectiveMass(a: GLObject3D, b: GLObject3D, rA: Vec3, rB: Vec3, axis: Vec3): Float {
        val denominator = a.invMass + b.invMass + angularTerm(a, rA, axis) + angularTerm(b, rB, axis)
        return if (denominator > 1e-8f) 1f / denominator else 0f
    }

    private fun angularTerm(body: GLObject3D, r: Vec3, axis: Vec3): Float {
        if (body.isStatic) return 0f
        val rCrossAxis = r.cross(axis)
        val iInvTerm = (body.Iinv * Vec4(rCrossAxis.x, rCrossAxis.y, rCrossAxis.z, 0f)).xyz
        return rCrossAxis dot iInvTerm
    }

    private fun relativeVelocity(c: PreparedContact): Vec3 {
        return c.bodyB.velocityAtPoint(c.point) - c.bodyA.velocityAtPoint(c.point)
    }

    private fun solveVelocity(c: PreparedContact) {
        // Normal impulse (non-penetration + restitution), accumulated impulse clamped to >= 0:
        // a contact can only push bodies apart, never pull them together.
        run {
            val vn = relativeVelocity(c) dot c.normal
            var lambda = -(vn - c.restitutionBias) * c.normalMass
            val newImpulse = max(0f, c.normalImpulse + lambda)
            lambda = newImpulse - c.normalImpulse
            c.normalImpulse = newImpulse
            applyImpulsePair(c, c.normal, lambda)
        }

        // Friction: Coulomb-clamped against the *current* accumulated normal impulse.
        val maxFriction = max(c.bodyA.friction, c.bodyB.friction) * c.normalImpulse

        run {
            val vt = relativeVelocity(c) dot c.tangent1
            var lambda = -vt * c.tangent1Mass
            val newImpulse = (c.tangent1Impulse + lambda).coerceIn(-maxFriction, maxFriction)
            lambda = newImpulse - c.tangent1Impulse
            c.tangent1Impulse = newImpulse
            applyImpulsePair(c, c.tangent1, lambda)
        }
        run {
            val vt = relativeVelocity(c) dot c.tangent2
            var lambda = -vt * c.tangent2Mass
            val newImpulse = (c.tangent2Impulse + lambda).coerceIn(-maxFriction, maxFriction)
            lambda = newImpulse - c.tangent2Impulse
            c.tangent2Impulse = newImpulse
            applyImpulsePair(c, c.tangent2, lambda)
        }
    }

    private fun applyImpulsePair(c: PreparedContact, direction: Vec3, magnitude: Float) {
        if (magnitude == 0f) return
        val impulse = direction * magnitude
        c.bodyA.applyImpulse(-impulse, c.point)
        c.bodyB.applyImpulse(impulse, c.point)
    }

    private fun solvePosition(c: PreparedContact) {
        val penetration = c.penetration - PENETRATION_SLOP
        if (penetration <= 0f) return
        val invMassA = c.bodyA.invMass
        val invMassB = c.bodyB.invMass
        val totalInvMass = invMassA + invMassB
        if (totalInvMass <= 0f) return

        val correctionMagnitude = (penetration * POSITION_CORRECTION_PERCENT) / totalInvMass
        val correction = c.normal * correctionMagnitude
        c.bodyA.correctPosition(correction * -invMassA)
        c.bodyB.correctPosition(correction * invMassB)
        // The bodies just moved along the normal; keep the cached penetration roughly in sync
        // so a later position iteration for the same contact doesn't over-correct.
        c.penetration -= correctionMagnitude * totalInvMass
    }
}
