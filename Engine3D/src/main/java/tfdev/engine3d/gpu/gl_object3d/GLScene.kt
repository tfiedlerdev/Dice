package tfdev.engine3d.gpu.gl_object3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.LightSource
import tfdev.engine3d.gpu.physics.Contact
import tfdev.engine3d.gpu.physics.BoundingBox
import tfdev.engine3d.gpu.physics.SATCollisionDetector
import java.nio.FloatBuffer

class GLScene : GLObject3D(gravity=Vec4(0f, 0f, 0f)) {
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
    
    private val satDetector = SATCollisionDetector()

    /**
     * Detect and resolve collisions between all objects in the scene using SAT
     */
    fun checkCollisions() {
        val contacts = mutableListOf<Contact>()
        
        // Detect collisions between all pairs of objects
        for ((i, child1) in children.withIndex()) {
            for ((j, child2) in children.withIndex()) {
                if (i >= j) continue // Avoid duplicate pairs and self-collision
                
                val collisionResult = satDetector.detectCollision(child1, child2)
                
                if (collisionResult.isColliding && collisionResult.separationAxis != null && collisionResult.contactPoint != null) {
                    println("Collision detected! Overlap: ${collisionResult.overlapDistance}, Axis: ${collisionResult.separationAxis}")
                    val contact = Contact(
                        objectA = child1,
                        objectB = child2,
                        contactPoint = collisionResult.contactPoint,
                        normal = collisionResult.separationAxis,
                        penetrationDepth = collisionResult.overlapDistance,
                        contactPointA = collisionResult.contactPoint - child1.pos,
                        contactPointB = collisionResult.contactPoint - child2.pos
                    )
                    contacts.add(contact)
                }
            }
        }
        
        // Resolve all contacts
        resolveContacts(contacts)
    }
    

    
    /**
     * Resolve all contacts using iterative impulse-based method
     */
    private fun resolveContacts(contacts: List<Contact>) {
        if (contacts.isEmpty()) return
        
        // Iterative resolution for stability
        val maxIterations = 8 // Increased for better convergence
        for (iteration in 0 until maxIterations) {
            var totalImpulse = 0f
            
            for (contact in contacts) {
                val oldImpulse = contact.calculateImpulse()
                contact.applyImpulse()
                val newImpulse = contact.calculateImpulse()
                totalImpulse += kotlin.math.abs(newImpulse - oldImpulse)
            }
            
            // If impulses are small, we've converged
            if (totalImpulse < 1e-6f) break
        }
        
        // Very conservative penetration resolution
        // Only apply when objects are nearly at rest and penetrating significantly
        for (contact in contacts) {
            val relativeVelocity = contact.getRelativeVelocity()
            val velocityMagnitude = relativeVelocity.length()
            
            // Resolve penetration more aggressively to prevent falling through
            if (contact.penetrationDepth > 0.005f) {
                contact.resolvePenetration()
            }
        }
    }
    
    /**
     * Legacy collision detection method (kept for backward compatibility)
     */
    fun checkCollisionsLegacy() {
        for ((i, child1) in children.withIndex()) {
            for ((j, child2) in children.withIndex()) {
                if (i == j) {
                    continue
                }
                val bb1 = child1.boundingBox
                val bb2 = child2.boundingBox

                if(bb1!=null && bb2!=null){
                    val collisionPoints = bb1.getCollidingEdgePointsWorld(bb2)
                    if(collisionPoints.isNotEmpty()){
                        val m1 = child1.mass
                        val m2 = child2.mass
                        val v1 = child1.velocity
                        val v2 = child2.velocity

                        val u1 = (v1*(m1-m2)+v2*(2*m2))/(m1+m2)
                        val u2 = (v2*(m2-m1)+v1*(2*m1))/(m2+m1)
                        child1.force.assign(-u1*child1.mass)
                        child2.force.assign(u2*child2.mass)
                    }
                }
            }
        }
    }
}