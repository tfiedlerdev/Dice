package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3

/**
 * A single point of contact between two colliding boxes.
 *
 * [normal] always points away from body A, towards body B (i.e. the
 * direction body B should be pushed to resolve the overlap).
 * [penetrationDepth] is how far the two boxes overlap along [normal] at
 * this specific point (can differ per point within the same manifold).
 */
class ContactPoint(
    val worldPoint: Vec3,
    val normal: Vec3,
    val penetrationDepth: Float
)
