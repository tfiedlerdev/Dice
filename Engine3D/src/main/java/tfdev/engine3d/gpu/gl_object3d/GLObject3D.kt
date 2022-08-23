package tfdev.engine3d.gpu.gl_object3d

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLES20.*
import android.util.Log
import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.object3d.Object3D
import tfdev.engine3d.Camera
import tfdev.engine3d.Transform
import java.nio.*

abstract class GLObject3D : Transform() {
    private val children: ArrayList<GLObject3D> = ArrayList()
    var parent: GLObject3D? = null

    abstract val openGLProgram: Int?
    abstract val vertexBuffer: FloatBuffer?
    abstract val vertexCount: Int?
    open val drawListBuffer: ShortBuffer? = null
    open val drawListLength: Int? = null
    open val uvBuffer: FloatBuffer? = null


    fun addChild(obj: GLObject3D) {
        obj.parent = this
        children.add(obj)
    }

    fun updateSelfAndChild() {
        if (!isDirty) {
            return
        }
        forceUpdateSelfAndChild()
    }

    private fun forceUpdateSelfAndChild() {
        val parent = this.parent
        if (parent == null) {
            computeModelMatrix()
        } else {
            computeModelMatrix(parent.modelMatrix)
        }
        for (child in children) {
            child.forceUpdateSelfAndChild()
        }
    }

    private var positionHandle: Int = 0
    private var mColorHandle: Int = 0


    private val vertexStride: Int = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

    private val color = floatArrayOf(1f, 0f, 0f, 1f)

    fun draw(projection: Matrix4x4) {
        drawSelf(projection)

        for (child in children) {
            child.draw(projection)
        }
    }

    protected open fun drawSelf(projection: Matrix4x4) {
        val program = openGLProgram
        val vertexCount = vertexCount
        if (program != null && vertexCount != null) {
            // Add program to OpenGL ES environment
            glUseProgram(program)

            // get handle to vertex shader's vPosition member
            positionHandle = glGetAttribLocation(program, "vPosition").also {

                // Enable a handle to the triangle vertices
                glEnableVertexAttribArray(it)

                // Prepare the triangle coordinate data
                glVertexAttribPointer(
                    it,
                    COORDS_PER_VERTEX,
                    GL_FLOAT,
                    false,
                    vertexStride,
                    vertexBuffer
                )

                // get handle to fragment shader's vColor member
                mColorHandle = glGetUniformLocation(program, "vColor").also { colorHandle ->

                    // Set color for drawing the triangle
                    glUniform4fv(colorHandle, 1, color, 0)
                }

                val vPMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
                // Pass the projection and view transformation to the shader
                val mvpMatrix = projection * modelMatrix
                glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix.data(), 0)

                val uvBuffer = uvBuffer
                if(uvBuffer!=null && false) {
                    val uvHandle = IntBuffer.allocate(1)
                    glGenBuffers(1, uvHandle)
                    glBindBuffer(GL_ARRAY_BUFFER, uvHandle[0])
                    glBufferData(GL_ARRAY_BUFFER, uvBuffer.capacity()*4, uvBuffer, GL_STATIC_DRAW)
                    glEnableVertexAttribArray(0)
                    glBindBuffer(GL_ARRAY_BUFFER, uvHandle[0]);
                    glVertexAttribPointer(
                        0,                                // attribute. No particular reason for 1, but must match the layout in the shader.
                        uvBuffer.capacity(),                                // size
                        GL_FLOAT,                         // type
                        false,                         // normalized?
                        0,                                // stride
                        0                          // array buffer offset
                    );
                }
                    // Draw the triangle
                val drawListLength = drawListLength
                if(drawListBuffer==null||drawListLength==null) {
                    glDrawArrays(GL_TRIANGLES, 0, vertexCount)
                }
                else{
                    glDrawElements(GL_TRIANGLES, drawListLength, GL_UNSIGNED_SHORT, drawListBuffer)
                }


                // Disable vertex array
                glDisableVertexAttribArray(it)
            }
        }
    }

    companion object {
        const val COORDS_PER_VERTEX = 3
        fun shortBufferFromArray(a: ShortArray): ShortBuffer {
            return ByteBuffer.allocateDirect(a.size * 4).run {
                // use the device hardware's native byte order
                order(ByteOrder.nativeOrder())

                // create a floating point buffer from the ByteBuffer
                asShortBuffer().apply {
                    // add the coordinates to the FloatBuffer
                    put(a)
                    // set the buffer to read the first coordinate
                    position(0)
                }
            }
        }
        fun floatBufferFromArray(a: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(a.size * 4).run {
                // use the device hardware's native byte order
                order(ByteOrder.nativeOrder())

                // create a floating point buffer from the ByteBuffer
                asFloatBuffer().apply {
                    // add the coordinates to the FloatBuffer
                    put(a)
                    // set the buffer to read the first coordinate
                    position(0)
                }
            }
        }
    }
}