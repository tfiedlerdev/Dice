package tfdev.engine3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4

/** Based on http://www.cs.cmu.edu/~baraff/sigcourse/notesd1.pdf */
open class Transform(
    val pos: Vec4 = Vec4(),
    val eulerRotDeg: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f)
) {
    val modelMatrix = Matrix4x4()
    val inverseModelMatrix = Matrix4x4()
    protected var _isDirty = true

    val R = Matrix4x4()


    private fun getLocalModelMatrix(): Matrix4x4 {
        val rotM = Matrix4x4.rotateEuler(eulerRotDeg.x, eulerRotDeg.y, eulerRotDeg.z)
        val transM = Matrix4x4().apply { translate(pos.x, pos.y, pos.z) }
        val scaleM = Matrix4x4().apply { scale(scale.x, scale.y, scale.z) }

        return transM * R * scaleM
    }

    fun computeModelMatrix() {
        modelMatrix.assign(getLocalModelMatrix())
        modelMatrix.inverse(inverseModelMatrix)
        _isDirty = false
    }

    fun computeModelMatrix(parentGlobalModelMatrix: Matrix4x4) {
        modelMatrix.assign(parentGlobalModelMatrix * getLocalModelMatrix())
        modelMatrix.inverse(inverseModelMatrix)
        _isDirty = false
    }

    open fun setDirty() {
        _isDirty = true
    }
    fun isDirty(): Boolean {
        return _isDirty
    }

    /**
     * This object's position in world space, i.e. including every ancestor's
     * transform - unlike [pos], which is relative to its parent (if any).
     * Requires [modelMatrix] to be up to date (see [computeModelMatrix]).
     */
    fun worldPosition(): Vec3 {
        return Vec3(modelMatrix[0, 3], modelMatrix[1, 3], modelMatrix[2, 3])
    }
}