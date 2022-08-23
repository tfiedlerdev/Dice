package tfdev.engine3d.gpu.gl_object3d

import android.content.Context
import android.opengl.GLES20
import tfdev.engine3d.gpu.shader.OpenGLProgram
import tfdev.engine3d.gpu.shader.Shader
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class GLSquare(context: Context) : GLObject3D() {
    private val squareCoords = floatArrayOf(
        -0.5f,  0.5f, 0.0f,      // top left
        -0.5f, -0.5f, 0.0f,      // bottom left
        0.5f, -0.5f, 0.0f,      // bottom right
        0.5f,  0.5f, 0.0f       // top right
    )

    override val openGLProgram = OpenGLProgram(
        Shader(context, GLES20.GL_VERTEX_SHADER, "triangle.vert"),
        Shader(context, GLES20.GL_FRAGMENT_SHADER, "triangle.frag")
    ).compile()
    override val vertexBuffer = floatBufferFromArray(squareCoords)
    override val vertexCount = squareCoords.size/ COORDS_PER_VERTEX
    override val drawListBuffer = shortBufferFromArray(shortArrayOf(0, 1, 2, 0, 2, 3))
    override val drawListLength = 6
}