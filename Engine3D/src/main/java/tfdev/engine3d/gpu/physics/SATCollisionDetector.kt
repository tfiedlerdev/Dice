package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

/**
 * SAT-based collision detector following the approach from:
 * https://www.atoft.dev/posts/2020/04/12/implementing-3d-collision-resolution/
 */
class SATCollisionDetector {
    
    data class CollisionResult(
        val isColliding: Boolean,
        val separationAxis: Vec3? = null,
        val overlapDistance: Float = 0f,
        val contactPoint: Vec4? = null
    )
    
    /**
     * Detect collision between two oriented bounding boxes using SAT
     */
    fun detectCollision(objA: GLObject3D, objB: GLObject3D): CollisionResult {
        // Get the basis vectors for both boxes
        val basisA = getBasisVectors(objA)
        val basisB = getBasisVectors(objB)
        
        var minOverlap = Float.MAX_VALUE
        var bestAxis: Vec3? = null
        var bestContactPoint: Vec4? = null
        
        // Test the 3 basis vectors of box A
        for (axis in basisA) {
            val (overlap, normal) = testAxis(objA, objB, axis)
            if (overlap <= 0) {
                return CollisionResult(false) // Separated on this axis
            }
            if (overlap < minOverlap) {
                minOverlap = overlap
                bestAxis = normal
                bestContactPoint = calculateContactPoint(objA, objB, normal)
            }
        }
        
        // Test the 3 basis vectors of box B
        for (axis in basisB) {
            val (overlap, normal) = testAxis(objA, objB, axis)
            if (overlap <= 0) {
                return CollisionResult(false) // Separated on this axis
            }
            if (overlap < minOverlap) {
                minOverlap = overlap
                bestAxis = normal
                bestContactPoint = calculateContactPoint(objA, objB, normal)
            }
        }
        
        // Test the 9 cross product axes
        for (axisA in basisA) {
            for (axisB in basisB) {
                val crossAxis = axisA.cross(axisB)
                if (crossAxis.length() > 1e-6f) { // Avoid zero-length axes
                    val normalizedAxis = crossAxis.normalize()
                    val (overlap, normal) = testAxis(objA, objB, normalizedAxis)
                    if (overlap <= 0) {
                        return CollisionResult(false) // Separated on this axis
                    }
                    if (overlap < minOverlap) {
                        minOverlap = overlap
                        bestAxis = normal
                        bestContactPoint = calculateContactPoint(objA, objB, normal)
                    }
                }
            }
        }
        
        // If we get here, the boxes are colliding
        return CollisionResult(
            isColliding = true,
            separationAxis = bestAxis,
            overlapDistance = minOverlap,
            contactPoint = bestContactPoint
        )
    }
    
    /**
     * Get the basis vectors (local axes) of an object
     */
    private fun getBasisVectors(obj: GLObject3D): Array<Vec3> {
        val rotationMatrix = obj.R
        return arrayOf(
            Vec3(rotationMatrix[0, 0], rotationMatrix[1, 0], rotationMatrix[2, 0]), // X axis
            Vec3(rotationMatrix[0, 1], rotationMatrix[1, 1], rotationMatrix[2, 1]), // Y axis
            Vec3(rotationMatrix[0, 2], rotationMatrix[1, 2], rotationMatrix[2, 2])  // Z axis
        )
    }
    
    /**
     * Test separation along a given axis and return overlap with correct normal direction
     */
    private fun testAxis(objA: GLObject3D, objB: GLObject3D, axis: Vec3): Pair<Float, Vec3> {
        val projectionA = projectBox(objA, axis)
        val projectionB = projectBox(objB, axis)
        
        val buffer = 0.01f
        
        // Check for separation (with buffer)
        if (projectionA.max + buffer < projectionB.min || projectionB.max + buffer < projectionA.min) {
            return Pair(0f, axis) // Separated
        }
        
        // Calculate overlap
        val overlap = kotlin.math.min(projectionA.max - projectionB.min, projectionB.max - projectionA.min)
        
        // Determine correct normal direction based on relative positions
        val centerA = objA.pos.xyz
        val centerB = objB.pos.xyz
        val direction = centerB - centerA
        
        // If direction points in same direction as axis, normal should point along axis
        // If direction points opposite to axis, normal should point opposite to axis
        val normalDirection = if (direction.dot(axis) > 0) axis else -axis
        
        return Pair(overlap + buffer, normalDirection)
    }
    
    /**
     * Project a box onto an axis
     */
    private fun projectBox(obj: GLObject3D, axis: Vec3): Projection {
        val corners = getBoxCorners(obj)
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        
        for (corner in corners) {
            val projection = corner.xyz.dot(axis)
            min = kotlin.math.min(min, projection)
            max = kotlin.math.max(max, projection)
        }
        
        return Projection(min, max)
    }
    
    /**
     * Get the world-space corners of a box
     */
    private fun getBoxCorners(obj: GLObject3D): Array<Vec4> {
        val scale = obj.scale
        val halfSize = Vec3(scale.x * 0.5f, scale.y * 0.5f, scale.z * 0.5f)
        
        return arrayOf(
            Vec4(-halfSize.x, -halfSize.y, -halfSize.z, 1f),
            Vec4( halfSize.x, -halfSize.y, -halfSize.z, 1f),
            Vec4(-halfSize.x,  halfSize.y, -halfSize.z, 1f),
            Vec4( halfSize.x,  halfSize.y, -halfSize.z, 1f),
            Vec4(-halfSize.x, -halfSize.y,  halfSize.z, 1f),
            Vec4( halfSize.x, -halfSize.y,  halfSize.z, 1f),
            Vec4(-halfSize.x,  halfSize.y,  halfSize.z, 1f),
            Vec4( halfSize.x,  halfSize.y,  halfSize.z, 1f)
        ).map { obj.modelMatrix * it }.toTypedArray()
    }
    
    /**
     * Calculate a contact point based on the separation axis
     */
    private fun calculateContactPoint(objA: GLObject3D, objB: GLObject3D, axis: Vec3): Vec4 {
        // For better contact points, find the deepest penetrating corner
        val cornersA = getBoxCorners(objA)
        val cornersB = getBoxCorners(objB)
        
        var bestContactPoint = (objA.pos + objB.pos) * 0.5f // Fallback
        var maxPenetration = 0f
        
        // Check corners of object B against object A
        for (corner in cornersB) {
            val cornerInA = objA.inverseModelMatrix * corner
            val penetration = calculatePenetrationDepth(cornerInA, objA.scale)
            if (penetration > maxPenetration) {
                maxPenetration = penetration
                bestContactPoint = corner
            }
        }
        
        // Check corners of object A against object B
        for (corner in cornersA) {
            val cornerInB = objB.inverseModelMatrix * corner
            val penetration = calculatePenetrationDepth(cornerInB, objB.scale)
            if (penetration > maxPenetration) {
                maxPenetration = penetration
                bestContactPoint = corner
            }
        }
        
        return bestContactPoint
    }
    
    /**
     * Calculate penetration depth for a point in local coordinates
     */
    private fun calculatePenetrationDepth(point: Vec4, scale: Vec3): Float {
        val absX = kotlin.math.abs(point.x)
        val absY = kotlin.math.abs(point.y)
        val absZ = kotlin.math.abs(point.z)
        
        val halfSizeX = scale.x * 0.5f
        val halfSizeY = scale.y * 0.5f
        val halfSizeZ = scale.z * 0.5f
        
        val penetrationX = halfSizeX - absX
        val penetrationY = halfSizeY - absY
        val penetrationZ = halfSizeZ - absZ
        
        return kotlin.math.max(0f, kotlin.math.min(kotlin.math.min(penetrationX, penetrationY), penetrationZ))
    }
    
    data class Projection(val min: Float, val max: Float)
} 