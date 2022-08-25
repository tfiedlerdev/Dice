package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

class LightSource(direction: Vec3,position: Vec3,color: FloatArray= floatArrayOf(1f, 1f, 1f, 1f)){
    val color = GLObject3D.floatBufferFromArray(color)
    val direction = GLObject3D.floatBufferFromArray(direction.data())
    val position = GLObject3D.floatBufferFromArray(position.data())
}