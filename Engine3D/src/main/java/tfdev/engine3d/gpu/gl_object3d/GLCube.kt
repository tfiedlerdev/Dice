package tfdev.engine3d.gpu.gl_object3d

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.physics.BoundingBox
import tfdev.engine3d.gpu.shader.OpenGLProgram
import tfdev.engine3d.gpu.shader.Shader

class GLCube(
    context: Context,
    addBoundingBox: Boolean = true,
    color: Vec4 = Vec4(1f, 1f, 1f, 1f),
    /** Draws the conventional 1-6 pip pattern per face (opposite faces sum to 7) instead of a plain color. */
    isDie: Boolean = false
) : GLObject3D(color) {

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
    // Per-vertex die face value (1-6, opposite faces sum to 7, as on a conventional die);
    // all zero - meaning "no pips" - for anything that isn't a die (the floor, walls, ...).
    // One repeated value per face, in the same bottom/right/back/left/front/top order as
    // cubeCoords/normalBufferData above.
    private val faceValueData: FloatArray = if (isDie) {
        floatArrayOf(
            6f, 6f, 6f, 6f, 6f, 6f, // bottom
            3f, 3f, 3f, 3f, 3f, 3f, // right
            5f, 5f, 5f, 5f, 5f, 5f, // back
            4f, 4f, 4f, 4f, 4f, 4f, // left
            2f, 2f, 2f, 2f, 2f, 2f, // front
            1f, 1f, 1f, 1f, 1f, 1f, // top
        )
    } else {
        FloatArray(36)
    }
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
    override val faceValueBuffer = floatBufferFromArray(faceValueData)
    override val boundingBox = if (addBoundingBox) BoundingBox(this) else null

    init {
        if (boundingBox != null) {
            if(DEBUG_CORNER_COLLISIONS) {
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
                        // A debug marker, not a physics body: isStatic excludes it from
                        // force/gravity integration, so it stays fixed in its parent's local space.
                        isStatic = true
                        setDirty()
                    }
                    addChild(indicator)
                    cornerCollisionIndicators.add(indicator)
                }
                boundingBox.onCornerCollisionCallback = { cornerIndex ->
                    cornerCollisionIndicators[cornerIndex].color.apply {
                        set(0, 1f)
                        set(1, 0f)
                        set(2, 0f)
                    }
                    Log.d("GLCube-onCornerColli", "Collision event at corner $cornerIndex")
                }
            }
            if(DEBUG_EDGE_COLLISIONS){
                val edgeCollisionIndicators = ArrayList<GLCube>()
                for ((corner1, corner2) in boundingBox.edges) {
                    val diff = corner2-corner1
                    val diffIndex = diff.data().indexOfFirst { it != 0f }
                    val indicator = GLCube(context, false,Vec4(0f,1f,0.5f)).apply {
                        pos.apply {
                            x = if(diffIndex==0) 0f else corner1.x
                            y = if(diffIndex==1) 0f else corner1.y
                            z = if(diffIndex==2) 0f else corner1.z
                        }
                        scale.apply {
                            x = if(diff.x==0f) 0.1f else diff.x*0.7f
                            y = if(diff.y==0f) 0.1f else diff.y*0.7f
                            z = if(diff.z==0f) 0.1f else diff.z*0.7f
                        }
                        // See the corner-indicator comment above.
                        isStatic = true

                        setDirty()
                    }
                    addChild(indicator)
                    edgeCollisionIndicators.add(indicator)
                }
                boundingBox.onEdgeCollisionCallback = { edgeIndex ->
                    edgeCollisionIndicators[edgeIndex].color.apply {
                        set(0, 1f)
                        set(1, 0f)
                        set(2, 0f)
                    }
                    Log.d("GLCube-onEdgeColli", "Collision event at edge $edgeIndex")
                }
            }
        }
    }
    companion object{
        const val DEBUG_CORNER_COLLISIONS = false
        const val DEBUG_EDGE_COLLISIONS = false
    }
}