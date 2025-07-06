package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

/**
 * Represents a contact point between two objects
 */
class Contact(
    val objectA: GLObject3D,
    val objectB: GLObject3D,
    val contactPoint: Vec4,
    val normal: Vec3,
    val penetrationDepth: Float,
    val contactPointA: Vec4 = Vec4(0f, 0f, 0f, 0f),
    val contactPointB: Vec4 = Vec4(0f, 0f, 0f, 0f)
) {
    private var accumulatedImpulse = 0f
    private val restitution = 0.3f // Bounciness factor
    private val friction = 0.8f // Friction coefficient
    
    /**
     * Calculate the relative velocity at the contact point
     */
    fun getRelativeVelocity(): Vec3 {
        val velocityA = objectA.velocity.xyz
        val velocityB = objectB.velocity.xyz
        
        val angularVelocityA = objectA.omega.xyz
        val angularVelocityB = objectB.omega.xyz
        
        val rA = contactPointA
        val rB = contactPointB
        
        val tangentialVelocityA = angularVelocityA.cross(rA.xyz)
        val tangentialVelocityB = angularVelocityB.cross(rB.xyz)
        
        return (velocityB + tangentialVelocityB) - (velocityA + tangentialVelocityA)
    }
    
    /**
     * Calculate the impulse needed to resolve this contact
     */
    fun calculateImpulse(): Float {
        val relativeVelocity = getRelativeVelocity()
        val normalVelocity = relativeVelocity.dot(normal)
        
        // If objects are moving apart, no impulse needed
        if (normalVelocity > 0) return 0f
        
        val invMassA = if (objectA.isStatic) 0f else 1f / objectA.mass
        val invMassB = if (objectB.isStatic) 0f else 1f / objectB.mass
        
        val rA = contactPointA
        val rB = contactPointB
        
        // Calculate effective mass
        val rACrossN = rA.xyz.cross(normal)
        val rBCrossN = rB.xyz.cross(normal)
        
        val invInertiaA = if (objectA.isStatic) Matrix4x4() else objectA.Iinv
        val invInertiaB = if (objectB.isStatic) Matrix4x4() else objectB.Iinv
        
        val angularTermA = if (objectA.isStatic) 0f else rACrossN.dot((invInertiaA * Vec4(rACrossN.x, rACrossN.y, rACrossN.z, 0f)).xyz)
        val angularTermB = if (objectB.isStatic) 0f else rBCrossN.dot((invInertiaB * Vec4(rBCrossN.x, rBCrossN.y, rBCrossN.z, 0f)).xyz)
        
        val effectiveMass = invMassA + invMassB + angularTermA + angularTermB
        
        if (effectiveMass <= 0) return 0f
        
        // Calculate impulse
        val restitutionTerm = 1f + restitution
        val impulse = -(normalVelocity * restitutionTerm) / effectiveMass
        
        println("Contact: normalVelocity=$normalVelocity, effectiveMass=$effectiveMass, normal=$normal, impulse=$impulse")
        
        return impulse
    }
    
    /**
     * Apply impulse to resolve the contact
     */
    fun applyImpulse() {
        val impulse = calculateImpulse()
        val impulseChange = impulse - accumulatedImpulse
        accumulatedImpulse = impulse
        
        println("Contact: impulse=$impulse, impulseChange=$impulseChange, objectA.isStatic=${objectA.isStatic}, objectB.isStatic=${objectB.isStatic}")
        
        if (impulseChange == 0f) return
        
        val impulseVector = normal * impulseChange
        
        // Apply linear impulse to momentum (not velocity)
        if (!objectA.isStatic) {
            val oldVelA = objectA.velocity.xyz
            val oldPA = objectA.P.xyz
            // Direct assignment to avoid potential issues with .xyz property
            objectA.P.x = objectA.P.x - impulseVector.x
            objectA.P.y = objectA.P.y - impulseVector.y
            objectA.P.z = objectA.P.z - impulseVector.z
            objectA.velocity.x = objectA.P.x / objectA.mass
            objectA.velocity.y = objectA.P.y / objectA.mass
            objectA.velocity.z = objectA.P.z / objectA.mass
            println("Contact: objectA momentum: $oldPA -> ${objectA.P.xyz}, velocity: $oldVelA -> ${objectA.velocity.xyz}")
        }
        if (!objectB.isStatic) {
            val oldVelB = objectB.velocity.xyz
            val oldPB = objectB.P.xyz
            // Direct assignment to avoid potential issues with .xyz property
            objectB.P.x = objectB.P.x + impulseVector.x
            objectB.P.y = objectB.P.y + impulseVector.y
            objectB.P.z = objectB.P.z + impulseVector.z
            objectB.velocity.x = objectB.P.x / objectB.mass
            objectB.velocity.y = objectB.P.y / objectB.mass
            objectB.velocity.z = objectB.P.z / objectB.mass
            println("Contact: objectB momentum: $oldPB -> ${objectB.P.xyz}, velocity: $oldVelB -> ${objectB.velocity.xyz}")
        }
        
        // Apply angular impulse
        val rA = contactPointA
        val rB = contactPointB
        
        val angularImpulseA = rA.xyz.cross(impulseVector)
        val angularImpulseB = rB.xyz.cross(impulseVector)
        
        if (!objectA.isStatic) {
            objectA.L.xyz = objectA.L.xyz + angularImpulseA
            objectA.omega.xyz = (objectA.Iinv * objectA.L).xyz
        }
        if (!objectB.isStatic) {
            objectB.L.xyz = objectB.L.xyz - angularImpulseB
            objectB.omega.xyz = (objectB.Iinv * objectB.L).xyz
        }
    }
    
    /**
     * Resolve penetration by moving objects apart
     * This is now much more conservative and only used when necessary
     */
    fun resolvePenetration() {
        if (penetrationDepth <= 0) return
        
        val invMassA = if (objectA.mass > 0) 1f / objectA.mass else 0f
        val invMassB = if (objectB.mass > 0) 1f / objectB.mass else 0f
        val totalInvMass = invMassA + invMassB
        
        if (totalInvMass <= 0) return
        
        // Calculate separation based on mass ratios
        val separationA = penetrationDepth * invMassA / totalInvMass
        val separationB = penetrationDepth * invMassB / totalInvMass
        
        // Apply very conservative separation
        val separationFactor = 0.1f // Only resolve 10% of penetration
        val moveVector = normal * (penetrationDepth * separationFactor)
        
        if (objectA.mass > 0) {
            objectA.pos.xyz = objectA.pos.xyz - moveVector * separationA / totalInvMass
        }
        if (objectB.mass > 0) {
            objectB.pos.xyz = objectB.pos.xyz + moveVector * separationB / totalInvMass
        }
    }
} 