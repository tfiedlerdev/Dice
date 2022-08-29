package tfdev.engine3d

import org.junit.Test
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.physics.BoundingBox

class BoundingBoxTest {
    @Test
    fun cornerCollisionTest(){
        val bbox1 = BoundingBox(Transform())
        val bbox2 = BoundingBox(Transform(pos= Vec3(0.5f,0.5f, 0.5f)).apply { computeModelMatrix() })


        val corner = bbox1.getCollidingCorner(bbox2)
        assert(corner == bbox2.CORNER_LEFT_BOTTOM_BACK)
    }
}