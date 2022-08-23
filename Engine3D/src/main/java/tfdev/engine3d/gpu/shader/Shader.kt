package tfdev.engine3d.gpu.shader

import android.content.Context
import android.opengl.GLES20

class Shader(private val type: Int, private val shaderCode: String) {
    constructor(context: Context, type: Int, assetFileName: String):this(type, readUTF8FileFromAssets(context, assetFileName))
    fun compile(): Int{
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        return GLES20.glCreateShader(type).also { shader ->

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object{
        fun readUTF8FileFromAssets(context: Context, assetFileName: String): String{
            return readFileFromAssets(context, assetFileName).decodeToString()
        }
        fun readFileFromAssets(context: Context, assetFileName: String): ByteArray {


            val iStream = context.assets.open(assetFileName)
            iStream.use { input ->
                return input.readBytes()
            }
        }
    }
}