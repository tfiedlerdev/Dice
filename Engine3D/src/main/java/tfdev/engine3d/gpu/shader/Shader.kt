package tfdev.engine3d.gpu.shader

import android.content.Context
import android.opengl.GLES20

class Shader(private val type: Int, private val shaderCode: String) {
    constructor(context: Context, type: Int, assetFileName: String):this(type, readUTF8FileFromAssets(context, assetFileName))
    val typeLabel = if(type == GLES20.GL_FRAGMENT_SHADER) "fragment" else "vertex"

    fun compile(): Int{
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        val shaderId = GLES20.glCreateShader(type).also { shader ->
            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shaderId)
            GLES20.glDeleteShader(shaderId)
            throw RuntimeException("Could not compile $typeLabel shader program: $log")
        }
        return shaderId
    }

    companion object{
        fun readUTF8FileFromAssets(context: Context, assetFileName: String): String{
            return readFileFromAssets(context, assetFileName).decodeToString()
        }
        private fun readFileFromAssets(context: Context, assetFileName: String): ByteArray {
            val iStream = context.assets.open(assetFileName)
            iStream.use { input ->
                return input.readBytes()
            }
        }

    }
}