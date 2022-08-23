package tfdev.engine3d.gpu.gl_object3d

import android.content.Context
import android.opengl.GLES20
import tfdev.engine3d.gpu.shader.OpenGLProgram
import tfdev.engine3d.gpu.shader.Shader
import java.nio.FloatBuffer

class GLCube(context: Context) : GLObject3D() {

    private val cubeCoords = floatArrayOf(
        -0.5f, 0f, 0.5f,        // lbb
        -0.5f, 0f, -0.5f,       // lbf
        -0.5f, 1f, 0.5f,        // ltb
        -0.5f, 1f, -0.5f,       // ltf
        0.5f, 0f, 0.5f,         // rbb
        0.5f, 0f, -0.5f,        // rbf
        0.5f, 1f, 0.5f,         // rtb
        0.5f, 1f, -0.5f         // rtf
    )
    private val drawOrder = shortArrayOf(
        0, 1, 2, // left side 1
        1, 3, 2, // left side 2
        6, 5, 4, // right side 1
        6, 3, 5, // right side 2
        5, 1, 0, // bottom side 1
        0, 4, 5, //bottom side 2
        2, 3, 7, // top side 1
        2, 6, 7, // top side 2
        5, 1, 7, // front side 1
        3, 1, 7, // front side 2
        6, 4, 0, // back side 1
        2, 6, 0 //  back side 2
    )
    private val uvData = floatArrayOf(
        0f, 1f,        // lbb
        0f, 0f,       // lbf
        0f, 1f,        // ltb
        0f, 0f,       // ltf
        1f, 1f,        // rbb
        1f, 0f,       // rbf
        1f, 1f,        // rtb
        1f, 0f        // rtf
    )
    override val openGLProgram = OpenGLProgram(
        Shader(context, GLES20.GL_VERTEX_SHADER, "triangle.vert"),
        Shader(context, GLES20.GL_FRAGMENT_SHADER, "triangle.frag")
    ).compile()
    override val vertexBuffer = floatBufferFromArray(cubeCoords)
    override val vertexCount = cubeCoords.size / COORDS_PER_VERTEX
    override val drawListBuffer = shortBufferFromArray(drawOrder)
    override val drawListLength = drawOrder.size
    override val uvBuffer = floatBufferFromArray(uvData)
}