package tfdev.engine3d.gpu.gl_object3d

import android.opengl.GLES20.*
import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import tfdev.engine3d.Transform
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

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
            val positionHandle = glGetAttribLocation(program, "vPosition")

            // Enable a handle to the triangle vertices
            glEnableVertexAttribArray(positionHandle)

            // Prepare the triangle coordinate data
            glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GL_FLOAT,
                false,
                vertexStride,
                vertexBuffer
            )

            // get handle to fragment shader's vColor member
            val colorHandle = glGetUniformLocation(program, "vColor")
            glUniform4fv(colorHandle, 1, color, 0)


            val vPMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
            // Pass the projection and view transformation to the shader
            val mvpMatrix = projection * modelMatrix
            glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix.data(), 0)

            val uvBuffer = uvBuffer
            if (uvBuffer != null) {
                val uvHandle = glGetAttribLocation(program, "vUV")

                // Enable a handle to the triangle vertices
                glEnableVertexAttribArray(uvHandle)

                // Prepare the triangle coordinate data
                glVertexAttribPointer(
                    uvHandle,
                    2,
                    GL_FLOAT,
                    false,
                    4*2,
                    uvBuffer
                )
            }
            // Draw the triangle
            val drawListLength = drawListLength
            if (drawListBuffer == null || drawListLength == null) {
                glDrawArrays(GL_TRIANGLES, 0, vertexCount)
            } else {
                glDrawElements(GL_TRIANGLES, drawListLength, GL_UNSIGNED_SHORT, drawListBuffer)
            }


            // Disable vertex array
            glDisableVertexAttribArray(positionHandle)

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