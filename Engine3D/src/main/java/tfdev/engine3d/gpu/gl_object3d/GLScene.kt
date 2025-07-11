package tfdev.engine3d.gpu.gl_object3d

import android.util.Log
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.LightSource
import tfdev.engine3d.physics.CollisionResolver
import java.nio.FloatBuffer

class GLScene : GLObject3D() {
    override val openGLProgram: Int?
        get() = null
    override val vertexBuffer: FloatBuffer?
        get() = null
    override val vertexCount: Int
        get() = 0
    override val lightSource = LightSource(
        Vec3(-0.2f, 1f, 0f), Vec3(0.75f, 1.5f, 0f),
        Vec4(1f, 1f, 1f, 1f)
    )
    
    private val collisionResolver = CollisionResolver()

    fun checkCollisions() {
        // Process each unique pair only once
        for (i in children.indices) {
            for (j in i + 1 until children.size) {
                val child1 = children[i]
                val child2 = children[j]
                val bb1 = child1.boundingBox
                val bb2 = child2.boundingBox

                if (bb1 != null && bb2 != null) {
                    // Check for detailed collision
                    val collisionInfo = bb1.checkCollisionDetailed(bb2, child1, child2)
                    
                    if (collisionInfo != null) {
                        // Log collision for debugging
                        Log.d("GLScene", "Collision detected between objects $i and $j")
                        Log.d("GLScene", "Contact point: ${collisionInfo.contactPoint}")
                        Log.d("GLScene", "Normal: ${collisionInfo.normal}")
                        Log.d("GLScene", "Penetration: ${collisionInfo.penetrationDepth}")
                        
                        // Resolve the collision using impulse-based physics
                        collisionResolver.resolveCollision(collisionInfo)
                    }
                }
            }
        }
    }
}