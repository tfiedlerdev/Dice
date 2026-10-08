package tfdev.engine3d.gpu.gl_object3d

import android.opengl.GLES20.*
import sensors_in_paradise.sonar.custom_views.stickman.math.Matrix4x4
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.DynamicTransform
import tfdev.engine3d.gpu.LightSource
import tfdev.engine3d.gpu.physics.BoundingBox
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

abstract class GLObject3D(color: Vec4 = Vec4(1f,1f,1f,1f)) : DynamicTransform() {
    val children: ArrayList<GLObject3D> = ArrayList()
    var parent: GLObject3D? = null

    abstract val openGLProgram: Int?
    abstract val vertexBuffer: FloatBuffer?
    abstract val vertexCount: Int?
    open val drawListBuffer: ShortBuffer? = null
    open val drawListLength: Int? = null
    open val uvBuffer: FloatBuffer? = null
    open val normalBuffer: FloatBuffer? = null

    /**
     * Optional per-vertex value (e.g. which die face 1-6 a vertex belongs to) the
     * fragment shader can use to vary what it draws per-face within a single draw
     * call, without a texture. Null means "not used" (plain [color] everywhere).
     */
    open val faceValueBuffer: FloatBuffer? = null
    open val lightSource: LightSource? = null
    open val boundingBox: BoundingBox? = null
    val color: FloatArray = floatArrayOf(color.x, color.y, color.z, color.w)

    /** Recolors this object in place - picked up on the next [drawSelf], no [setDirty] needed. */
    fun setColor(newColor: Vec4) {
        color[0] = newColor.x
        color[1] = newColor.y
        color[2] = newColor.z
        color[3] = newColor.w
    }

    private fun getObjectLightSource(): LightSource? {
        if (lightSource != null) {
            return lightSource
        }
        return this.parent?.getObjectLightSource()
    }

    fun addChild(obj: GLObject3D) {
        obj.parent = this
        children.add(obj)
    }

    fun removeChild(obj: GLObject3D) {
        if (children.remove(obj)) {
            obj.parent = null
        }
    }

    fun updateSelfAndChild() {
        if (!isDirty()) {
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



    fun draw(projection: Matrix4x4) {
        drawSelf(projection)

        for (child in children) {
            child.draw(projection)
        }
    }

    fun step(deltaTime: Long) {
        if (stepPhysics(deltaTime)) {
            setDirty()
        }

        for (child in children) {
            child.step(deltaTime)
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
            val colorHandle = glGetUniformLocation(program, "uColor")
            glUniform4fv(colorHandle, 1, color, 0)


            val vPMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
            // Pass the projection and view transformation to the shader
            val mvpMatrix = projection * modelMatrix
            glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix.data(), 0)

            // Pass themodel transformation to the shader
            val modelMatrixHandle = glGetUniformLocation(program, "uModelMatrix")
            val modelMatrix = modelMatrix
            glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix.data(), 0)

            val lightSource = getObjectLightSource()
            if(lightSource!=null){
                val lightPositionHandle = glGetUniformLocation(program, "uLightPosition")
                glUniform3fv(lightPositionHandle, 1, lightSource.position)
                val lightColorHandle = glGetUniformLocation(program, "uLightColor")
                glUniform4fv(lightColorHandle, 1, lightSource.color)
                val secondaryDirectionHandle = glGetUniformLocation(program, "uSecondaryLightDirection")
                glUniform3fv(secondaryDirectionHandle, 1, lightSource.secondaryDirection)
                val pointLightEnabledHandle = glGetUniformLocation(program, "uPointLightEnabled")
                glUniform1f(pointLightEnabledHandle, if (lightSource.pointLightEnabled) 1f else 0f)
                val secondaryLightEnabledHandle = glGetUniformLocation(program, "uSecondaryLightEnabled")
                glUniform1f(secondaryLightEnabledHandle, if (lightSource.secondaryLightEnabled) 1f else 0f)
            }

            val uvBuffer = uvBuffer
            if (uvBuffer != null) {
                val uvHandle = glGetAttribLocation(program, "vUV")

                glEnableVertexAttribArray(uvHandle)

                glVertexAttribPointer(
                    uvHandle,
                    2,
                    GL_FLOAT,
                    false,
                    4*2,
                    uvBuffer
                )
            }
            val normalBuffer = normalBuffer
            if (normalBuffer != null) {
                val normalHandle = glGetAttribLocation(program, "vNormal")


                glEnableVertexAttribArray(normalHandle)


                glVertexAttribPointer(
                    normalHandle,
                    3,
                    GL_FLOAT,
                    false,
                    4*3,
                    normalBuffer
                )
            }
            val faceValueBuffer = faceValueBuffer
            if (faceValueBuffer != null) {
                val faceValueHandle = glGetAttribLocation(program, "vFaceValue")
                if (faceValueHandle >= 0) {
                    glEnableVertexAttribArray(faceValueHandle)
                    glVertexAttribPointer(
                        faceValueHandle,
                        1,
                        GL_FLOAT,
                        false,
                        4,
                        faceValueBuffer
                    )
                }
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
    override fun setDirty() {
        super.setDirty()

        parent?.setDirty()
    }
}