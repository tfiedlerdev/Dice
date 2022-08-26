package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.Transform

open class DynamicTransform(
    val constantForce: Vec3 = Vec3(0f, 0f, 0f),
    val force: Vec3 = Vec3(0f, 0f, 0f),
    val velocity: Vec3 = Vec3(0f, 0f, 0f),
    var mass: Float = 1f
) : Transform() {

    open fun step(deltaTime: Long) {
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