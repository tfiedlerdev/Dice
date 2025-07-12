package tfdev.engine3d.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import sensors_in_paradise.sonar.custom_views.stickman.math.cross
import tfdev.engine3d.gpu.DynamicTransform
import kotlin.math.max
import kotlin.math.min

class CollisionResolver {
    
    fun resolveCollision(collision: CollisionInfo) {
        val objA = collision.objectA
        val objB = collision.objectB
        
        // GLObject3D extends DynamicTransform, so we can cast directly
        val dynamicA = objA as? DynamicTransform
        val dynamicB = objB as? DynamicTransform
        
        if (dynamicA == null && dynamicB == null) return
        
        // Calculate relative velocity at contact point
        val contactA = if (dynamicA != null) collision.contactPoint - dynamicA.pos else Vec4(0f, 0f, 0f)
        val contactB = if (dynamicB != null) collision.contactPoint - dynamicB.pos else Vec4(0f, 0f, 0f)
        
        val velA = if (dynamicA != null) {
            dynamicA.velocity + (dynamicA.omega cross contactA)
        } else Vec4(0f, 0f, 0f)
        
        val velB = if (dynamicB != null) {
            dynamicB.velocity + (dynamicB.omega cross contactB)
        } else Vec4(0f, 0f, 0f)
        
        val relativeVelocity = velA - velB
        
        // Calculate relative velocity along collision normal
        val velocityAlongNormal = relativeVelocity dot collision.normal
        
        // Do not resolve if velocities are separating
        if (velocityAlongNormal > 0) return
        
        // Calculate restitution (bounciness)
        val restitution = if (dynamicA != null && dynamicB != null) {
            min(dynamicA.restitution, dynamicB.restitution)
        } else {
            dynamicA?.restitution ?: dynamicB?.restitution ?: 0.5f
        }
        
        // Calculate impulse scalar
        var impulseScalar = -(1 + restitution) * velocityAlongNormal
        
        // Add mass and inertia contributions
        val invMassA = if (dynamicA != null) 1f / dynamicA.mass else 0f
        val invMassB = if (dynamicB != null) 1f / dynamicB.mass else 0f
        
        impulseScalar /= invMassA + invMassB
        
        // Add rotational inertia contribution
        if (dynamicA != null) {
            val raCrossN = contactA cross collision.normal
            val angularContribA = (dynamicA.Iinv * raCrossN) dot raCrossN
            impulseScalar /= (1f + angularContribA * invMassA)
        }
        
        if (dynamicB != null) {
            val rbCrossN = contactB cross collision.normal
            val angularContribB = (dynamicB.Iinv * rbCrossN) dot rbCrossN
            impulseScalar /= (1f + angularContribB * invMassB)
        }
        
        // Apply impulse
        val impulse = collision.normal * impulseScalar
        
        if (dynamicA != null) {
            dynamicA.P += impulse
            dynamicA.L += contactA cross impulse
            dynamicA.velocity.assign(dynamicA.P / dynamicA.mass)
            dynamicA.omega.assign(dynamicA.Iinv * dynamicA.L)
        }
        
        if (dynamicB != null) {
            dynamicB.P -= impulse
            dynamicB.L -= contactB cross impulse
            dynamicB.velocity.assign(dynamicB.P / dynamicB.mass)
            dynamicB.omega.assign(dynamicB.Iinv * dynamicB.L)
        }
        
        // Apply friction
        applyFriction(collision, dynamicA, dynamicB, impulseScalar, contactA, contactB)
        
        // Resolve penetration
        resolvePenetration(collision, dynamicA, dynamicB)
    }
    
    private fun applyFriction(
        collision: CollisionInfo,
        dynamicA: DynamicTransform?,
        dynamicB: DynamicTransform?,
        normalImpulse: Float,
        contactA: Vec4,
        contactB: Vec4
    ) {
        // Calculate relative velocity
        val velA = if (dynamicA != null) {
            dynamicA.velocity + (dynamicA.omega cross contactA)
        } else Vec4(0f, 0f, 0f)
        
        val velB = if (dynamicB != null) {
            dynamicB.velocity + (dynamicB.omega cross contactB)
        } else Vec4(0f, 0f, 0f)
        
        val relativeVelocity = velA - velB
        
        // Calculate tangent vector (perpendicular to normal)
        val velocityAlongNormal = (relativeVelocity dot collision.normal) * collision.normal
        val tangentVelocity = relativeVelocity - velocityAlongNormal
        
        if (tangentVelocity.lengthSquared() < 0.0001f) return
        
        val tangent = tangentVelocity.normalized()
        
        // Calculate friction coefficient
        val friction = if (dynamicA != null && dynamicB != null) {
            (dynamicA.friction + dynamicB.friction) * 0.5f
        } else {
            dynamicA?.friction ?: dynamicB?.friction ?: 0.3f
        }
        
        // Calculate friction impulse magnitude
        val frictionImpulseMagnitude = friction * kotlin.math.abs(normalImpulse)
        
        // Apply friction impulse
        val frictionImpulse = tangent * -frictionImpulseMagnitude
        
        if (dynamicA != null) {
            dynamicA.P += frictionImpulse
            dynamicA.L += contactA cross frictionImpulse
            dynamicA.velocity.assign(dynamicA.P / dynamicA.mass)
            dynamicA.omega.assign(dynamicA.Iinv * dynamicA.L)
        }
        
        if (dynamicB != null) {
            dynamicB.P -= frictionImpulse
            dynamicB.L -= contactB cross frictionImpulse
            dynamicB.velocity.assign(dynamicB.P / dynamicB.mass)
            dynamicB.omega.assign(dynamicB.Iinv * dynamicB.L)
        }
    }
    
    private fun resolvePenetration(
        collision: CollisionInfo,
        dynamicA: DynamicTransform?,
        dynamicB: DynamicTransform?
    ) {
        if (collision.penetrationDepth <= 0) return
        
        val totalInvMass = (if (dynamicA != null) 1f / dynamicA.mass else 0f) +
                          (if (dynamicB != null) 1f / dynamicB.mass else 0f)
        
        if (totalInvMass <= 0) return
        
        val penetrationResolution = collision.normal * (collision.penetrationDepth / totalInvMass)
        
        if (dynamicA != null) {
            val moveA = penetrationResolution * (1f / dynamicA.mass)
            dynamicA.pos += moveA
        }
        
        if (dynamicB != null) {
            val moveB = penetrationResolution * (1f / dynamicB.mass)
            dynamicB.pos -= moveB
        }
    }
}