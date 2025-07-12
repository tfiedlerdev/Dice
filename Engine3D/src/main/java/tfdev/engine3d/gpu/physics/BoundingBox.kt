package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform
import tfdev.engine3d.gpu.gl_object3d.GLObject3D
import tfdev.engine3d.physics.CollisionInfo
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max


class BoundingBox(
    val transform: Transform,
    var onCornerCollisionCallback: ((cornerIndex: Int) -> Unit)? = null,
    var onEdgeCollisionCallback: ((edgeIndex: Int) -> Unit)? = null
) {


    val corners = arrayOf(
        Vec4(-0.5f, -0.5f, 0.5f),
        Vec4(-0.5f, -0.5f, -0.5f),
        Vec4(-0.5f, 0.5f, 0.5f),
        Vec4(-0.5f, 0.5f, -0.5f),
        Vec4(0.5f, -0.5f, 0.5f),
        Vec4(0.5f, -0.5f, -0.5f),
        Vec4(0.5f, 0.5f, 0.5f),
        Vec4(0.5f, 0.5f, -0.5f),
    )

    val CORNER_LEFT_BOTTOM_FRONT = corners[0]
    val CORNER_LEFT_BOTTOM_BACK = corners[1]
    val CORNER_LEFT_TOP_FRONT = corners[2]
    val CORNER_LEFT_TOP_BACK = corners[3]
    val CORNER_RIGHT_BOTTOM_FRONT = corners[4]
    val CORNER_RIGHT_BOTTOM_BACK = corners[5]
    val CORNER_RIGHT_TOP_FRONT = corners[6]
    val CORNER_RIGHT_TOP_BACK = corners[7]


    val edges = arrayOf(
        Pair(corners[0], corners[1]),
        Pair(corners[2], corners[3]),
        Pair(corners[4], corners[5]),
        Pair(corners[6], corners[7]),
        Pair(corners[0], corners[2]),
        Pair(corners[1], corners[3]),
        Pair(corners[4], corners[6]),
        Pair(corners[5], corners[7]),
        Pair(corners[0], corners[4]),
        Pair(corners[1], corners[5]),
        Pair(corners[2], corners[6]),
        Pair(corners[3], corners[7]),
    )

    init {
        assert(corners.size == 8)

    }


    fun getCollidingEdgePointsWorld(bb2: BoundingBox): List<Vec3> {
        val collisionPoints= ArrayList<Vec3>(8)
        for ((index, edge) in bb2.edges.withIndex()) {
            val (p1, p2) = edge
            // transform point so that it is positioned relative to this bbox which is axis aligned and in origin


            val p1World = transform.inverseModelMatrix * bb2.transform.modelMatrix*p1
            val p2World = transform.inverseModelMatrix * bb2.transform.modelMatrix*p2

            val intersection = CheckLineBox(CORNER_LEFT_BOTTOM_BACK.xyz, CORNER_RIGHT_TOP_FRONT.xyz, p1World.xyz, p2World.xyz)
            if(intersection!=null){
                bb2.onEdgeCollisionCallback?.let {
                    it(index)
                }
                collisionPoints.add(intersection)
            }
        }
        return collisionPoints
    }

    private fun isRayColliding(ray: Pair<Vec4, Vec4>) {

    }

    fun CheckLineBox(B1: Vec3, B2: Vec3, L1: Vec3, L2: Vec3): Vec3? {
        if (L2.x < B1.x && L1.x < B1.x) return null
        if (L2.x > B2.x && L1.x > B2.x) return null
        if (L2.y < B1.y && L1.y < B1.y) return null
        if (L2.y > B2.y && L1.y > B2.y) return null
        if (L2.z < B1.z && L1.z < B1.z) return null
        if (L2.z > B2.z && L1.z > B2.z) return null
        if (L1.x > B1.x && L1.x < B2.x &&
            L1.y > B1.y && L1.y < B2.y &&
            L1.z > B1.z && L1.z < B2.z
        ) {
            return L1
        }

        val potentialIntersections = arrayOf(
            GetIntersection(L1.x - B1.x, L2.x - B1.x, L1, L2),
            GetIntersection(L1.y - B1.y, L2.y - B1.y, L1, L2),
            GetIntersection(L1.z - B1.z, L2.z - B1.z, L1, L2),
            GetIntersection(L1.x - B2.x, L2.x - B2.x, L1, L2),
            GetIntersection(L1.y - B2.y, L2.y - B2.y, L1, L2),
            GetIntersection(L1.z - B2.z, L2.z - B2.z, L1, L2)
        )
        for ((index, intersection) in potentialIntersections.withIndex()) {
            if (intersection != null) {
                if (InBox(intersection, B1, B2, (index % 3) + 1)) {
                    return intersection
                }
            }
        }
        return null
    }

    fun GetIntersection(fDst1: Float, fDst2: Float, P1: Vec3, P2: Vec3): Vec3? {
        if ((fDst1 * fDst2) >= 0.0f) return null
        if (fDst1 == fDst2) return null
        return P1 + (P2 - P1) * (-fDst1 / (fDst2 - fDst1));
    }

    fun InBox(Hit: Vec3, B1: Vec3, B2: Vec3, Axis: Int): Boolean {
        if (Axis == 1 && Hit.z > B1.z && Hit.z < B2.z && Hit.y > B1.y && Hit.y < B2.y) return true;
        if (Axis == 2 && Hit.z > B1.z && Hit.z < B2.z && Hit.x > B1.x && Hit.x < B2.x) return true;
        if (Axis == 3 && Hit.x > B1.x && Hit.x < B2.x && Hit.y > B1.y && Hit.y < B2.y) return true;
        return false;
    }

    fun getCollidingCorner(bb2: BoundingBox): Vec4? {
        if (!transform.isDirty() && !bb2.transform.isDirty()) {
            for ((index, corner) in bb2.corners.withIndex()) {
                // transform point so that it is positioned relative to this bbox which is axis aligned and in origin
                val cornerWorld = bb2.transform.modelMatrix * corner
                val p = transform.inverseModelMatrix * cornerWorld
                if (isPointInThisBox(p)) {
                    bb2.onCornerCollisionCallback?.let {
                        it(index)
                    }
                    return corner
                }
            }
        }
        return null
    }

    private fun isPointInThisBox(p: Vec4): Boolean {
        if (abs(p.x) > 0.5) {
            return false
        }
        if (abs(p.y) > 0.5) {
            return false
        }
        if (abs(p.z) > 0.5) {
            return false
        }
        return true
    }
    
    fun checkCollisionDetailed(other: BoundingBox, objA: GLObject3D, objB: GLObject3D): CollisionInfo? {
        // Get world space AABBs
        val minA = getMinWorld()
        val maxA = getMaxWorld()
        val minB = other.getMinWorld()
        val maxB = other.getMaxWorld()
        
        // Check AABB overlap
        if (minA.x > maxB.x || maxA.x < minB.x) return null
        if (minA.y > maxB.y || maxA.y < minB.y) return null
        if (minA.z > maxB.z || maxA.z < minB.z) return null
        
        // Calculate overlap on each axis
        val overlapX = min(maxA.x, maxB.x) - max(minA.x, minB.x)
        val overlapY = min(maxA.y, maxB.y) - max(minA.y, minB.y)
        val overlapZ = min(maxA.z, maxB.z) - max(minA.z, minB.z)
        
        // Find axis of minimum overlap (separation axis)
        var normal = Vec4(0f, 0f, 0f)
        var penetration = Float.MAX_VALUE
        
        if (overlapX < overlapY && overlapX < overlapZ) {
            penetration = overlapX
            normal = if (transform.pos.x < other.transform.pos.x) Vec4(1f, 0f, 0f) else Vec4(-1f, 0f, 0f)
        } else if (overlapY < overlapZ) {
            penetration = overlapY
            normal = if (transform.pos.y < other.transform.pos.y) Vec4(0f, 1f, 0f) else Vec4(0f, -1f, 0f)
        } else {
            penetration = overlapZ
            normal = if (transform.pos.z < other.transform.pos.z) Vec4(0f, 0f, 1f) else Vec4(0f, 0f, -1f)
        }
        
        // Calculate contact point (center of overlap region)
        val contactPoint = Vec4(
            (max(minA.x, minB.x) + min(maxA.x, maxB.x)) * 0.5f,
            (max(minA.y, minB.y) + min(maxA.y, maxB.y)) * 0.5f,
            (max(minA.z, minB.z) + min(maxA.z, maxB.z)) * 0.5f
        )
        
        return CollisionInfo(
            objectA = objA,
            objectB = objB,
            contactPoint = contactPoint,
            normal = normal,
            penetrationDepth = penetration,
            isFaceCollision = true
        )
    }
    
    private fun getMinWorld(): Vec4 {
        val worldCorners = corners.map { corner ->
            transform.modelMatrix * corner
        }
        return Vec4(
            worldCorners.minOf { it.x },
            worldCorners.minOf { it.y },
            worldCorners.minOf { it.z }
        )
    }
    
    private fun getMaxWorld(): Vec4 {
        val worldCorners = corners.map { corner ->
            transform.modelMatrix * corner
        }
        return Vec4(
            worldCorners.maxOf { it.x },
            worldCorners.maxOf { it.y },
            worldCorners.maxOf { it.z }
        )
    }
}