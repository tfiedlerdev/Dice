package tfdev.engine3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3

open class Transform(
    val pos: Vec3 = Vec3(),
    val eulerRotDeg: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f)
) {
    val modelMatrix = Matrix4x4()
    val inverseModelMatrix = Matrix4x4()
    protected var isDirty = true

    private fun getLocalModelMatrix(): Matrix4x4 {
        val rotM = Matrix4x4.rotateEuler(eulerRotDeg.x, eulerRotDeg.y, eulerRotDeg.z)
        val transM = Matrix4x4().apply { translate(pos.x, pos.y, pos.z) }
        val scaleM = Matrix4x4().apply { scale(scale.x, scale.y, scale.z) }

        return transM * rotM * scaleM
    }

    fun computeModelMatrix() {
        modelMatrix.copyFrom(getLocalModelMatrix())
        modelMatrix.inverse(inverseModelMatrix)
        isDirty = false
    }

    fun computeModelMatrix(parentGlobalModelMatrix: Matrix4x4) {
        modelMatrix.copyFrom(parentGlobalModelMatrix * getLocalModelMatrix())
        modelMatrix.inverse(inverseModelMatrix)
        isDirty = false
    }

    fun setDirty() {
        isDirty = true
    }
}