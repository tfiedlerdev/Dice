package tfdev.engine3d.gpu.gl_object3d

import java.nio.FloatBuffer

class GLScene: GLObject3D() {
    override val openGLProgram: Int?
        get() = null
    override val vertexBuffer: FloatBuffer?
        get() = null
    override val vertexCount: Int
        get() = 0
}