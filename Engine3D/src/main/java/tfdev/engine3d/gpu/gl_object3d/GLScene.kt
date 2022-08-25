package tfdev.engine3d.gpu.gl_object3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.LightSource
import java.nio.FloatBuffer

class GLScene: GLObject3D() {
    override val openGLProgram: Int?
        get() = null
    override val vertexBuffer: FloatBuffer?
        get() = null
    override val vertexCount: Int
        get() = 0
    override val lightSource = LightSource(Vec3(-0.2f,1f,0f), Vec3(0.75f,1.5f,0f),floatArrayOf(1f, 1f, 1f))
}