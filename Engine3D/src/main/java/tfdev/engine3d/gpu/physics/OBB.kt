package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3

/**
 * A snapshot of an oriented bounding box in world space: a box of
 * [halfExtents] along three orthonormal [axes], centered at [center].
 *
 * All engine objects are rendered as a unit cube (local corners at
 * +-0.5 on every axis) carried through [tfdev.engine3d.Transform.modelMatrix].
 * Extracting the box straight from that world matrix (rather than
 * recombining `scale` and rotation separately) is what keeps this correct
 * for rotated AND parented/nested objects without ever double-applying scale.
 */
class OBB(
    val center: Vec3,
    val axes: Array<Vec3>,
    val halfExtents: Vec3
) {
    /** Half extent along [axes]\[index] (0=x, 1=y, 2=z, in local box space). */
    fun halfExtent(index: Int): Float = when (index) {
        0 -> halfExtents.x
        1 -> halfExtents.y
        else -> halfExtents.z
    }

    /** The 8 world-space corners of the box. */
    fun corners(): Array<Vec3> {
        val result = ArrayList<Vec3>(8)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sy in floatArrayOf(-1f, 1f)) {
                for (sz in floatArrayOf(-1f, 1f)) {
                    result.add(
                        center +
                            axes[0] * (sx * halfExtents.x) +
                            axes[1] * (sy * halfExtents.y) +
                            axes[2] * (sz * halfExtents.z)
                    )
                }
            }
        }
        return result.toTypedArray()
    }

    /** Projects the box onto [axis] and returns how far its extent reaches in either direction from its center. */
    fun projectedRadius(axis: Vec3): Float {
        return kotlin.math.abs(axes[0] dot axis) * halfExtents.x +
            kotlin.math.abs(axes[1] dot axis) * halfExtents.y +
            kotlin.math.abs(axes[2] dot axis) * halfExtents.z
    }

    companion object {
        /**
         * Builds the world-space OBB of a unit cube (-0.5..0.5 locally)
         * transformed by [modelMatrix].
         */
        fun fromModelMatrix(modelMatrix: Matrix4x4): OBB {
            val center = Vec3(modelMatrix[0, 3], modelMatrix[1, 3], modelMatrix[2, 3])
            val axes = arrayOf(
                modelMatrix.getAxis3(0),
                modelMatrix.getAxis3(1),
                modelMatrix.getAxis3(2)
            )
            val halfExtents = Vec3(
                modelMatrix.getColumnLength3(0) * 0.5f,
                modelMatrix.getColumnLength3(1) * 0.5f,
                modelMatrix.getColumnLength3(2) * 0.5f
            )
            return OBB(center, axes, halfExtents)
        }
    }
}
