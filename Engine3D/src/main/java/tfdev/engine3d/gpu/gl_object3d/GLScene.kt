package tfdev.engine3d.gpu.gl_object3d

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.LightSource
import java.nio.FloatBuffer

class GLScene : GLObject3D() {
    override val openGLProgram: Int?
        get() = null
    override val vertexBuffer: FloatBuffer?
        get() = null
    override val vertexCount: Int
        get() = 0
    override val lightSource = LightSource(
        Vec3(-0.2f, 1f, 0f), Vec3(0.75f, 1.5f, 0f),
        Vec4(1f, 1f, 1f, 1f)
    )

    fun checkCollisions() {
        for ((i, child1) in children.withIndex()) {
            for ((j, child2) in children.withIndex()) {
                if (i == j) {
                    continue
                }
                val bb1 = child1.boundingBox
                val bb2 = child2.boundingBox

                if(bb1!=null && bb2!=null){
                    bb1.getCollidingCorner(bb2)
                }
            }
        }
    }
}