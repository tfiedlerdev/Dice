package tfdev.engine3d.gpu.gl_object3d

import android.content.Context
import android.opengl.GLES20
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.physics.BoundingBox
import tfdev.engine3d.gpu.shader.OpenGLProgram
import tfdev.engine3d.gpu.shader.Shader

class GLCube(context: Context, addBoundingBox: Boolean = true) : GLObject3D() {

    private val cubeCoords = floatArrayOf(
        -0.5f, -0.5f, 0.5f, // 0 bottom
        -0.5f, -0.5f, -0.5f,// 1
        0.5f, -0.5f, -0.5f, // 5
        -0.5f, -0.5f, 0.5f, // 0
        0.5f, -0.5f, -0.5f, // 5
        0.5f, -0.5f, 0.5f,  // 4
        0.5f, 0.5f, 0.5f,  // 6 right
        0.5f, -0.5f, -0.5f, // 5
        0.5f, -0.5f, 0.5f,  // 4
        0.5f, 0.5f, 0.5f,  // 6
        0.5f, 0.5f, -0.5f, // 7
        0.5f, -0.5f, -0.5f, // 5
        0.5f, 0.5f, 0.5f,  // 6 back
        0.5f, -0.5f, 0.5f,  // 4
        -0.5f, -0.5f, 0.5f, // 0
        0.5f, 0.5f, 0.5f,  // 6
        -0.5f, -0.5f, 0.5f, // 0
        -0.5f, 0.5f, 0.5f, // 2
        -0.5f, -0.5f, 0.5f, // 0 left
        -0.5f, -0.5f, -0.5f,// 1
        -0.5f, 0.5f, 0.5f, // 2
        -0.5f, 0.5f, -0.5f,// 3
        -0.5f, 0.5f, 0.5f, // 2
        -0.5f, -0.5f, -0.5f,// 1
        -0.5f, -0.5f, -0.5f,// 1 front
        0.5f, -0.5f, -0.5f, // 5
        0.5f, 0.5f, -0.5f, // 7
        -0.5f, -0.5f, -0.5f,// 1
        0.5f, 0.5f, -0.5f, // 7
        -0.5f, 0.5f, -0.5f,// 3
        -0.5f, 0.5f, 0.5f, // 2 top
        -0.5f, 0.5f, -0.5f,// 3
        0.5f, 0.5f, -0.5f, // 7
        -0.5f, 0.5f, 0.5f, // 2
        0.5f, 0.5f, -0.5f, // 7
        0.5f, 0.5f, 0.5f,  // 6
    )
    private val normalBufferData = floatArrayOf(
        0f, -1f, 0f, // bottom
        0f, -1f, 0f,
        0f, -1f, 0f,
        0f, -1f, 0f,
        0f, -1f, 0f,
        0f, -1f, 0f,
        1f, 0f, 0f, // right
        1f, 0f, 0f,
        1f, 0f, 0f,
        1f, 0f, 0f,
        1f, 0f, 0f,
        1f, 0f, 0f,
        0f, 0f, -1f, // back
        0f, 0f, -1f,
        0f, 0f, -1f,
        0f, 0f, -1f,
        0f, 0f, -1f,
        0f, 0f, -1f,
        -1f, 0f, 0f, // left
        -1f, 0f, 0f,
        -1f, 0f, 0f,
        -1f, 0f, 0f,
        -1f, 0f, 0f,
        -1f, 0f, 0f,
        0f, 0f, 1f, // front
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 1f, 0f, // top
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f,
    )
    private val uvData = floatArrayOf(
        0f, 0f, // bottom
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 1f,
        1f, 0f,
        1f, 1f, // right
        0f, 0f,
        1f, 0f,
        1f, 1f,
        0f, 1f,
        0f, 0f,
        0f, 1f, // back
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f,
        0f, 0f, // left
        1f, 0f,
        0f, 1f,
        1f, 1f,
        0f, 1f,
        1f, 0f,
        0f, 0f, // front
        1f, 0f,
        1f, 1f,
        0f, 0f,
        1f, 1f,
        0f, 1f,
        0f, 1f, // top
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )
    private val uvData2 = floatArrayOf(
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

    //override val drawListBuffer = shortBufferFromArray(drawOrder)
    //override val drawListLength = drawOrder.size
    override val uvBuffer = floatBufferFromArray(uvData)
    override val normalBuffer = floatBufferFromArray(normalBufferData)
    override val boundingBox = if (addBoundingBox) BoundingBox(this) else null

    init {
        if (boundingBox != null) {
            val cornerCollisionIndicators = ArrayList<GLCube>()
            for (corner in boundingBox.corners) {
                val indicator = GLCube(context, false).apply {
                    pos.apply {
                        x = corner.x
                        y = corner.y
                        z = corner.z
                    }
                    scale.apply {
                        x = 0.2f
                        y = 0.2f
                        z = 0.2f
                    }
                    mass = Float.MAX_VALUE
                    setDirty()
                }
                addChild(indicator)
                cornerCollisionIndicators.add(indicator)
            }
            boundingBox.onCornerCollisionCallback = { cornerIndex ->
                cornerCollisionIndicators[cornerIndex].color.apply {
                    set(0,1f)
                    set(1,0f)
                    set(2,0f)
                }
            }
        }
    }
}