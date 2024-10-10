package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform

open class DynamicTransform(
    val constantForce: Vec4 = Vec4(0f, 0f, 0f),
    val force: Vec4 = Vec4(0f, 0f, 0f),
    val velocity: Vec4 = Vec4(0f, 0f, 0f),
    var mass: Float = 1f
) : Transform() {
    val Iinv = Matrix4x4()
    val omega = Vec4()
    val torque = Vec4()
    val P = Vec4()
    val L = Vec4()
    val Ibody = Matrix4x4(
        floatArrayOf(
            2f, 0f, 0f, 0f
        ),
        floatArrayOf(0f, 2f, 0f, 0f),
        floatArrayOf(0f, 0f, 2f, 0f),
        floatArrayOf(0f, 0f, 0f, 1f)
    )*(mass/12f)
    val IbodyInverse = Ibody.inverseClone()

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


        //P.assign(force)
        //L.assign(torque)
        // TODO: also check for rotational changes
        velocity.assign(P / mass)
        Iinv.assign(R * IbodyInverse * R.transposeClone())
        omega.assign(Iinv * L)
        val deltaPos = (velocity.xyz * dt)


        pos += deltaPos
        //R.assign(Matrix4x4.star(omega) * R)
        P += force *dt
        L += torque *dt

        force.zeros()
        torque.zeros()

        return !deltaPos.isZero()
    }

}