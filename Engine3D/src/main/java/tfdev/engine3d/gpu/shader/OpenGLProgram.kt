package tfdev.engine3d.gpu.shader

import android.opengl.GLES20

class OpenGLProgram(private val vertexShader: Shader,private val fragmentShader: Shader) {
    fun compile(): Int{
        return GLES20.glCreateProgram().also {

            // add the vertex shader to program
            GLES20.glAttachShader(it, vertexShader.compile())

            // add the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader.compile())

            // creates OpenGL ES program executables
            GLES20.glLinkProgram(it)
        }
    }
}