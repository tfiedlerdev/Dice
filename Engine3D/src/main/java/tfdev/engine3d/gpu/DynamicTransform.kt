package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

open class DynamicTransform(
    val constantForce: Vec4 = Vec4(0f, 0f, 0f),
    val force: Vec4 = Vec4(0f, 0f, 0f),
    val velocity: Vec4 = Vec4(0f, 0f, 0f),
    var mass: Float = 1f,
    val gravity: Vec4 = Vec4(0f, 0f, 0f)
) : Transform() {
    // Angular momentum and angular velocity
    val L = Vec4() // Angular momentum in world space
    val omega = Vec4() // Angular velocity in world space
    
    // Linear momentum
    val P = Vec4() // Linear momentum
    
    // Torque
    val torque = Vec4()
    
    // Inertia tensors
    val Ibody = Matrix4x4(
        floatArrayOf(2f, 0f, 0f, 0f),
        floatArrayOf(0f, 2f, 0f, 0f),
        floatArrayOf(0f, 0f, 2f, 0f),
        floatArrayOf(0f, 0f, 0f, 1f)
    ) * (mass / 12f) // For a unit cube
    
    val IbodyInverse = Ibody.inverseClone()
    val Iinv = Matrix4x4() // World space inverse inertia tensor
    
    // Gravity (can be overridden)

    
    // Physics state
    var isStatic = false
        set(value) {
            field = value
            if (value) {
                velocity.zeros()
                omega.zeros()
                P.zeros()
                L.zeros()
                gravity.zeros()
            }
        }
    var restitution = 0.8f // Bounciness factor
    var friction = 0.3f // Friction coefficient
    var angularDamping = 0.95f // Angular velocity damping factor (0.95 = 5% reduction per frame)
    var linearDamping = 0.98f // Linear velocity damping factor (0.98 = 2% reduction per frame)
    
    

    /**
     * Main physics integration step using semi-implicit Euler
     * This properly handles both linear and rotational motion
     */
    protected fun stepPhysics(deltaTime: Long): Boolean {
        if (isStatic) return false
        
        val dt = deltaTime.toFloat() / 1000f
        
        // Apply gravity
        val totalForce = force + gravity * mass
        
        // Integrate linear momentum: P(t+dt) = P(t) + F * dt
        P += totalForce * dt
        
        // Update linear velocity: v = P / m
        velocity.assign(P / mass)
        
        // Apply linear damping to gradually slow down
        velocity.assign(velocity * linearDamping)
        P.assign(P * linearDamping)
        
        // Apply more aggressive damping when near rest
        val velocityMagnitude = velocity.xyz.length()
        if (velocityMagnitude < 0.1f) {
            val restDamping = 0.9f // More aggressive damping when slow
            velocity.assign(velocity * restDamping)
            P.assign(P * restDamping)
        }
        
        // Integrate position: x(t+dt) = x(t) + v * dt
        val deltaPos = velocity.xyz * dt
        pos += deltaPos
        
        // Update world space inverse inertia tensor: I^(-1) = R * I_body^(-1) * R^T
        Iinv.assign(R * IbodyInverse * R.transposeClone())
        
        // Integrate angular momentum: L(t+dt) = L(t) + τ * dt
        L += torque * dt
        
        // Update angular velocity: ω = I^(-1) * L
        omega.assign(Iinv * L)
        
        // Apply angular damping to gradually stop spinning
        omega.assign(omega * angularDamping)
        L.assign(L * angularDamping)
        
        // Integrate rotation: R(t+dt) = R(t) * exp(ω * dt)
        // Using the exponential map for small rotations
        val omegaMagnitude = omega.xyz.length()
        if (omegaMagnitude > 1e-6f) {
            val omegaNormalized = omega.xyz / omegaMagnitude
            val rotationAngle = omegaMagnitude * dt
            
            // Create rotation matrix from axis-angle
            val rotationMatrix = Matrix4x4.rotateAxisAngle(rotationAngle, omegaNormalized)
            R.assign(rotationMatrix * R)
        }
        
        // Clear forces and torques for next frame
        force.zeros()
        torque.zeros()
        
        // Mark as dirty if there was movement
        val hasMovement = !deltaPos.isZero() || omegaMagnitude > 1e-6f
        if (hasMovement) {
            setDirty()
        }
        
        return hasMovement
    }
    
    /**
     * Apply impulse at a specific point in world space
     * This is crucial for proper collision response
     */
    fun applyImpulse(impulse: Vec4, point: Vec4) {
        if (isStatic) return
        
        // Linear impulse: ΔP = J
        P += impulse
        
        // Angular impulse: ΔL = r × J
        val r = point - pos // Vector from center of mass to contact point
        val angularImpulse = r.cross(impulse)
        L += Vec4(angularImpulse.x, angularImpulse.y, angularImpulse.z, 0f)
        
        setDirty()
    }
    
    /**
     * Get the velocity at a specific point in world space
     * This accounts for both linear and angular velocity
     */
    fun getVelocityAtPoint(point: Vec4): Vec4 {
        val r = point - pos // Vector from center of mass to point
        val angularVelocityAtPoint = omega.cross(r)
        return velocity + Vec4(angularVelocityAtPoint.x, angularVelocityAtPoint.y, angularVelocityAtPoint.z, 0f)
    }
    
    /**
     * Get the kinetic energy of the object
     */
    fun getKineticEnergy(): Float {
        val linearKE = 0.5f * mass * velocity.xyz.lengthSquared()
        val angularKE = 0.5f * (L.xyz dot (Iinv * L).xyz)
        return linearKE + angularKE
    }
    
    /**
     * Legacy method for backward compatibility
     */
    open fun stepOld(deltaTime: Long) {
        val dt = deltaTime.toFloat() / 1000f
        
        force += constantForce * mass
        velocity += (force / mass) * dt
        val acceleration = force / mass
        velocity += acceleration * dt
        pos += velocity * dt
        force.zeros()
        setDirty()
    }
}