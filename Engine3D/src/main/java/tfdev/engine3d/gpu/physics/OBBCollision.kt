package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import kotlin.math.abs

/**
 * Oriented-bounding-box vs oriented-bounding-box collision detection and
 * contact manifold generation.
 *
 * Detection is the classic 15-axis Separating Axis Theorem test for two
 * convex boxes (3 face axes of A, 3 face axes of B, 9 cross products of
 * their edge directions) - see Ericson, "Real-Time Collision Detection",
 * section 4.4.1. Instead of stopping at a single deepest *corner* (which is
 * what makes a box resting flat on a floor rock/jitter between corners
 * frame to frame), the minimum-penetration axis is then used to generate a
 * full contact manifold:
 *  - if the axis is a face normal of either box, the incident box's closest
 *    face is clipped against the reference face's side planes
 *    (Sutherland-Hodgman), producing up to 4 contact points - this is what
 *    makes a box resting on another box (or the floor) stable.
 *  - if the axis is an edge-edge cross product, the two contributing edges
 *    are reduced to their closest points, producing a single contact point.
 */
object OBBCollision {

    private const val PARALLEL_EPSILON = 1e-5f

    // Edge-edge axes are more numerically sensitive than face axes and, when
    // nearly tied with a face axis, produce a far less stable single-point
    // manifold. Require them to be clearly (not just marginally) better
    // before preferring them over a face contact.
    private const val FACE_CONTACT_BIAS = 0.01f

    private sealed class Axis {
        data class FaceA(val index: Int) : Axis()
        data class FaceB(val index: Int) : Axis()
        data class EdgeEdge(val indexA: Int, val indexB: Int) : Axis()
    }

    private class BestAxis(val worldAxis: Vec3, val overlap: Float, val axis: Axis)

    /**
     * Returns the contact manifold between [a] and [b], or an empty list if
     * they are not overlapping. Every [ContactPoint.normal] in the result
     * points from [a] towards [b].
     */
    fun detect(a: OBB, b: OBB): List<ContactPoint> {
        val best = findMinimumPenetrationAxis(a, b) ?: return emptyList()
        return when (val axis = best.axis) {
            is Axis.FaceA -> generateFaceContact(
                reference = a, referenceAxisIndex = axis.index, incident = b,
                referenceOutwardNormal = best.worldAxis, contactNormal = best.worldAxis
            )
            is Axis.FaceB -> generateFaceContact(
                reference = b, referenceAxisIndex = axis.index, incident = a,
                referenceOutwardNormal = -best.worldAxis, contactNormal = best.worldAxis
            )
            is Axis.EdgeEdge -> listOf(
                generateEdgeContact(a, b, axis.indexA, axis.indexB, best.worldAxis, best.overlap)
            )
        }
    }

    private fun findMinimumPenetrationAxis(a: OBB, b: OBB): BestAxis? {
        val centerDiff = b.center - a.center
        var minOverlap = Float.MAX_VALUE
        var bestWorldAxis: Vec3? = null
        var bestAxisInfo: Axis? = null

        // Returns false if a separating axis was found (callers should abort immediately).
        fun consider(rawAxis: Vec3, info: Axis, bias: Float): Boolean {
            val length = rawAxis.length()
            if (length < PARALLEL_EPSILON) return true // degenerate (near-parallel edges): no information, skip

            val axis = rawAxis / length
            val oriented = if ((centerDiff dot axis) < 0f) -axis else axis
            val distance = abs(centerDiff dot oriented)
            val overlap = a.projectedRadius(oriented) + b.projectedRadius(oriented) - distance
            if (overlap <= 0f) return false

            if (overlap + bias < minOverlap) {
                minOverlap = overlap
                bestWorldAxis = oriented
                bestAxisInfo = info
            }
            return true
        }

        for (i in 0..2) if (!consider(a.axes[i], Axis.FaceA(i), 0f)) return null
        for (i in 0..2) if (!consider(b.axes[i], Axis.FaceB(i), 0f)) return null
        for (i in 0..2) {
            for (j in 0..2) {
                if (!consider(a.axes[i].cross(b.axes[j]), Axis.EdgeEdge(i, j), FACE_CONTACT_BIAS)) return null
            }
        }

        val worldAxis = bestWorldAxis ?: return null
        val axisInfo = bestAxisInfo ?: return null
        return BestAxis(worldAxis, minOverlap, axisInfo)
    }

    // --- Face contact: clip the incident box's closest face against the reference face ---

    private fun generateFaceContact(
        reference: OBB,
        referenceAxisIndex: Int,
        incident: OBB,
        referenceOutwardNormal: Vec3,
        contactNormal: Vec3
    ): List<ContactPoint> {
        val (incidentAxisIndex, incidentSign) = bestIncidentFace(incident, referenceOutwardNormal)
        var polygon: List<Vec3> = faceCorners(incident, incidentAxisIndex, incidentSign).toList()

        val refHalfExtentAlongNormal = reference.halfExtent(referenceAxisIndex)
        for (otherAxisIndex in 0..2) {
            if (otherAxisIndex == referenceAxisIndex) continue
            val axisDir = reference.axes[otherAxisIndex]
            val halfExtent = reference.halfExtent(otherAxisIndex)

            // Plane at +halfExtent: keep points on the inner (-axisDir) side.
            polygon = clipPolygonToPlane(
                polygon,
                reference.center + axisDir * halfExtent,
                -axisDir
            )
            if (polygon.isEmpty()) return emptyList()

            // Plane at -halfExtent: keep points on the inner (+axisDir) side.
            polygon = clipPolygonToPlane(
                polygon,
                reference.center - axisDir * halfExtent,
                axisDir
            )
            if (polygon.isEmpty()) return emptyList()
        }

        val contacts = ArrayList<ContactPoint>(polygon.size)
        for (point in polygon) {
            val penetration = refHalfExtentAlongNormal - ((point - reference.center) dot referenceOutwardNormal)
            if (penetration >= -1e-4f) {
                contacts.add(ContactPoint(point, contactNormal, penetration.coerceAtLeast(0f)))
            }
        }
        return contacts
    }

    private fun bestIncidentFace(box: OBB, referenceOutwardNormal: Vec3): Pair<Int, Float> {
        var bestIndex = 0
        var bestSign = 1f
        var bestDot = Float.MAX_VALUE
        for (k in 0..2) {
            for (sign in floatArrayOf(1f, -1f)) {
                val dot = (box.axes[k] * sign) dot referenceOutwardNormal
                if (dot < bestDot) {
                    bestDot = dot
                    bestIndex = k
                    bestSign = sign
                }
            }
        }
        return Pair(bestIndex, bestSign)
    }

    /** The 4 corners of one face of [box], in cyclic (loop) order. */
    private fun faceCorners(box: OBB, axisIndex: Int, sign: Float): Array<Vec3> {
        val u = (axisIndex + 1) % 3
        val v = (axisIndex + 2) % 3
        val faceCenter = box.center + box.axes[axisIndex] * (sign * box.halfExtent(axisIndex))
        val eu = box.axes[u] * box.halfExtent(u)
        val ev = box.axes[v] * box.halfExtent(v)
        return arrayOf(
            faceCenter - eu - ev,
            faceCenter + eu - ev,
            faceCenter + eu + ev,
            faceCenter - eu + ev
        )
    }

    /** Sutherland-Hodgman: keeps the part of [polygon] where `(p - planePoint).planeNormal >= 0`. */
    private fun clipPolygonToPlane(polygon: List<Vec3>, planePoint: Vec3, planeNormal: Vec3): List<Vec3> {
        if (polygon.isEmpty()) return polygon
        val result = ArrayList<Vec3>(polygon.size + 2)
        for (i in polygon.indices) {
            val current = polygon[i]
            val previous = polygon[(i + polygon.size - 1) % polygon.size]
            val currentInside = ((current - planePoint) dot planeNormal) >= 0f
            val previousInside = ((previous - planePoint) dot planeNormal) >= 0f
            if (currentInside) {
                if (!previousInside) {
                    result.add(intersectEdgePlane(previous, current, planePoint, planeNormal))
                }
                result.add(current)
            } else if (previousInside) {
                result.add(intersectEdgePlane(previous, current, planePoint, planeNormal))
            }
        }
        return result
    }

    private fun intersectEdgePlane(a: Vec3, b: Vec3, planePoint: Vec3, planeNormal: Vec3): Vec3 {
        val ab = b - a
        val denom = ab dot planeNormal
        val t = if (abs(denom) < 1e-8f) 0f else (((planePoint - a) dot planeNormal) / denom).coerceIn(0f, 1f)
        return a + ab * t
    }

    // --- Edge-edge contact: closest points between the two contributing edges ---

    private fun generateEdgeContact(
        a: OBB,
        b: OBB,
        indexA: Int,
        indexB: Int,
        normal: Vec3,
        overlap: Float
    ): ContactPoint {
        val dirA = a.axes[indexA]
        val dirB = b.axes[indexB]

        // NOTE: using `pointOnA = pointOnA + ...` rather than `+=` is deliberate - VecBase's
        // `+=` mutates its receiver in place, and we must never mutate `a.center`/`b.center`.
        var pointOnA = a.center
        for (k in 0..2) {
            if (k == indexA) continue
            val sign = if (((b.center - a.center) dot a.axes[k]) >= 0f) 1f else -1f
            pointOnA = pointOnA + a.axes[k] * (sign * a.halfExtent(k))
        }

        var pointOnB = b.center
        for (k in 0..2) {
            if (k == indexB) continue
            val sign = if (((a.center - b.center) dot b.axes[k]) >= 0f) 1f else -1f
            pointOnB = pointOnB + b.axes[k] * (sign * b.halfExtent(k))
        }

        val r = pointOnA - pointOnB
        val bCoef = dirA dot dirB
        val f = dirB dot r
        val c = dirA dot r
        val denom = 1f - bCoef * bCoef

        var s = if (abs(denom) < 1e-8f) 0f else (bCoef * f - c) / denom
        var t = if (abs(denom) < 1e-8f) 0f else (f - bCoef * c) / denom

        s = s.coerceIn(-a.halfExtent(indexA), a.halfExtent(indexA))
        t = t.coerceIn(-b.halfExtent(indexB), b.halfExtent(indexB))

        val closestOnA = pointOnA + dirA * s
        val closestOnB = pointOnB + dirB * t
        val midpoint = (closestOnA + closestOnB) * 0.5f

        return ContactPoint(midpoint, normal, overlap.coerceAtLeast(0f))
    }
}
