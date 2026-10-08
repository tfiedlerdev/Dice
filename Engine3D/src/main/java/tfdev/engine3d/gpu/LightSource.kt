package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

/** A point light at [position] - its direction to any given fragment is computed per-fragment in the shader. */
class LightSource(position: Vec3, color: Vec4 = Vec4(1f, 1f, 1f, 1f)) {
    val color = GLObject3D.floatBufferFromArray(color.data())
    val position = GLObject3D.floatBufferFromArray(position.data())

    /** Moves the light. Absolute puts leave the buffer's read position untouched, so this is safe to call at any time, including between frames. */
    fun setPosition(newPosition: Vec3) {
        position.put(0, newPosition.x)
        position.put(1, newPosition.y)
        position.put(2, newPosition.z)
    }
}
