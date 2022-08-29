package tfdev.engine3d.gpu.gl_object3d

import android.util.Log
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
                    //bb1.getCollidingCorner(bb2)
                    val collisionPoints = bb1.getCollidingEdgePointsWorld(bb2)
                    if(collisionPoints.isNotEmpty()){
                        val m1 = child1.mass
                        val m2 = child2.mass
                        val v1 = child1.velocity
                        val v2 = child2. velocity


                        val u1 = (v1*(m1-m2)+v2*(2*m2))/(m1+m2)
                        val u2 = (v2*(m2-m1)+v1*(2*m1))/(m2+m1)
                        Log.d("GLScene", "Velocity 1 before: $v1, after: $u1. Velocity 2 before: $v2, after: $u2")
                        child1.force.assign(-u1*child1.mass)
                        child2.force.assign(u2*child2.mass)
                    }
                }
            }
        }
    }
}