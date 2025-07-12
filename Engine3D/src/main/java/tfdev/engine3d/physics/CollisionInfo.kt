package tfdev.engine3d.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

data class CollisionInfo(
    val objectA: GLObject3D,
    val objectB: GLObject3D,
    val contactPoint: Vec4,      // World space contact point
    val normal: Vec4,            // Collision normal (from A to B)
    val penetrationDepth: Float, // How much objects overlap
    val isEdgeCollision: Boolean = false,
    val isFaceCollision: Boolean = false
)