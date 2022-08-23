package tfdev.engine3d.gpu.gl_object3d

import android.content.Context
import android.opengl.GLES20
import tfdev.engine3d.gpu.shader.OpenGLProgram
import tfdev.engine3d.gpu.shader.Shader
import java.nio.FloatBuffer

class GLTriangle(context: Context) : GLObject3D() {
    private val triangleCoords = floatArrayOf(     // in counterclockwise order:
        0.0f, 0.622008459f, 0.0f,      // top
        -0.5f, -0.311004243f, 0.0f,    // bottom left
        0.5f, -0.311004243f, 0.0f      // bottom right
    )

    override val openGLProgram = OpenGLProgram(
        Shader(context, GLES20.GL_VERTEX_SHADER, "triangle.vert"),
        Shader(context, GLES20.GL_FRAGMENT_SHADER, "triangle.frag")
    ).compile()
    override val vertexBuffer = floatBufferFromArray(triangleCoords)
    override val vertexCount = triangleCoords.size

}