package tfdev.engine3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4

class Camera(val center: Vec3, eye: Vec3, val up: Vec3, private val onCameraChanged: (() -> Unit)? = null) {
    val eye = Vec4(eye)
    val lookAtMatrix = Matrix4x4.lookAt(eye, center, up)

    fun rotateY(degrees: Float, shouldNotifyThatCameraChanged: Boolean = true) {
        eye *= Matrix4x4().apply { rotateY(degrees) }
        if (shouldNotifyThatCameraChanged) {
            notifyCameraChanged()
        }
    }

    /** Places the eye along [direction] (need not be normalized) at [distance] from [center]. */
    fun setEyeDirection(direction: Vec3, distance: Float) {
        val normalized = direction.normalize()
        eye.x = center.x + normalized.x * distance
        eye.y = center.y + normalized.y * distance
        eye.z = center.z + normalized.z * distance
        notifyCameraChanged()
    }

	fun notifyCameraChanged() {
        Matrix4x4.lookAt(eye.xyz, center, up, lookAtMatrix)
        onCameraChanged?.let { it() }
    }
}
