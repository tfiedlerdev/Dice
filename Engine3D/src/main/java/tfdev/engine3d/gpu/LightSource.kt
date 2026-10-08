package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

/**
 * The scene's lighting: a point light straight overhead (at [position], direction
 * computed per-fragment in the shader) and a second, fixed-direction angled light
 * (just a direction - always "infinitely far away", so no position/attenuation) - the
 * point light alone leaves vertical surfaces like walls lit only by ambient light,
 * since they're nearly edge-on to a light directly above; the angled light is what
 * actually shows them. Its direction is diagonal (equal parts each horizontal axis)
 * rather than aligned with either wall axis, so it grazes two adjacent walls instead
 * of picking out just one and leaving the other three uniformly flat.
 */
class LightSource(position: Vec3, color: Vec4 = Vec4(1f, 1f, 1f, 1f)) {
    val color = GLObject3D.floatBufferFromArray(color.data())
    val position = GLObject3D.floatBufferFromArray(position.data())
    val secondaryDirection = GLObject3D.floatBufferFromArray(Vec3(1f, 1f, 1f).normalize().data())

    /** Moves the overhead light. Absolute puts leave the buffer's read position untouched, so this is safe to call at any time, including between frames. */
    fun setPosition(newPosition: Vec3) {
        position.put(0, newPosition.x)
        position.put(1, newPosition.y)
        position.put(2, newPosition.z)
    }
}
