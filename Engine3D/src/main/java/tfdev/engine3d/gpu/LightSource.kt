package tfdev.engine3d.gpu

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

class LightSource(direction: Vec3,position: Vec3,color: Vec4 = Vec4(1f, 1f, 1f, 1f)){
    val color = GLObject3D.floatBufferFromArray(color.data())
    val direction = GLObject3D.floatBufferFromArray(direction.data())
    val position = GLObject3D.floatBufferFromArray(position.data())
}