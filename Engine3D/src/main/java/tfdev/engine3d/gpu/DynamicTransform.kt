package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform

open class DynamicTransform(
    val constantForce: Vec4 = Vec4(0f, 0f, 0f),
    val force: Vec4 = Vec4(0f, 0f, 0f),
    val velocity: Vec4 = Vec4(0f, 0f, 0f),
    mass: Float = 1f,
    var restitution: Float = 0.5f,  // Bounciness: 0 = no bounce, 1 = perfect bounce
    var friction: Float = 0.3f       // Friction coefficient
) : Transform() {
    var mass: Float = mass
        set(value) {
            field = value
            updateInertia()
        }
    val Iinv = Matrix4x4()
    val omega = Vec4()
    val torque = Vec4()
    val P = Vec4()
    val L = Vec4()
    // Inertia tensor for a cube with side length 1
    var Ibody = Matrix4x4()
    var IbodyInverse = Matrix4x4()
    
    init {
        updateInertia()
    }
    
    private fun updateInertia() {
        // For a cube with unit size, I = (1/6) * m * side^2
        // Since our cube has side = 1, I = m/6 for each axis
        val inertia = mass / 6f
        Ibody = Matrix4x4(
            floatArrayOf(inertia, 0f, 0f, 0f),
            floatArrayOf(0f, inertia, 0f, 0f),
            floatArrayOf(0f, 0f, inertia, 0f),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        IbodyInverse = Ibody.inverseClone()
    }

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

    protected fun stepPhysics(deltaTime: Long): Boolean {
        val dt = deltaTime.toFloat() / 1000f

        // Apply constant forces (like gravity)
        force += constantForce * mass

        // Update momentum
        P += force * dt
        L += torque * dt
        
        // Calculate velocities from momentum
        velocity.assign(P / mass)
        Iinv.assign(R * IbodyInverse * R.transposeClone())
        omega.assign(Iinv * L)
        
        // Update position and rotation
        val deltaPos = velocity.xyz * dt
        pos += deltaPos
        
        // TODO: Update rotation matrix from angular velocity
        // R += Matrix4x4.star(omega) * R * dt

        // Clear forces for next frame
        force.zeros()
        torque.zeros()

        return !deltaPos.isZero()
    }

}