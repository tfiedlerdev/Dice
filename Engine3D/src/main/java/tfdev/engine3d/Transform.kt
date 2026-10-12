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

    /**
     * The actual rotation state - read by rendering and integrated in place by physics
     * (see DynamicTransform.stepPhysics) once a body starts moving. [eulerRotDeg] is
     * only a one-time initial setter for this (see [setDirty]), not a live transform -
     * nothing keeps it in sync with R afterward, so reading it back later won't
     * reflect the body's actual current orientation.
     */
    val R = Matrix4x4()
    private var eulerRotDegApplied = false

    private fun getLocalModelMatrix(): Matrix4x4 {
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

    /**
     * Besides the usual "needs its matrices recomputed" flag, this is also where
     * [eulerRotDeg] actually takes effect: the first call seeds [R] from its current
     * value, since callers conventionally set eulerRotDeg once right after construction
     * and then call this - see e.g. MainActivity.addDieOnGlThread. Guarded to exactly
     * once so later calls (physics moving the body calls this every step it moves) don't
     * keep resetting R back to that same initial value.
     */
    open fun setDirty() {
        _isDirty = true
        if (!eulerRotDegApplied) {
            R.assign(Matrix4x4.rotateEuler(eulerRotDeg.x, eulerRotDeg.y, eulerRotDeg.z))
            eulerRotDegApplied = true
        }
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