package tfdev.engine3d.gpu.physics

import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform
import kotlin.math.abs


class BoundingBox(val t: Transform, var onCornerCollisionCallback: ((cornerIndex: Int)-> Unit)? = null) {


    val corners=  arrayOf(
        Vec4(-0.5f, -0.5f, 0.5f),
        Vec4(-0.5f, -0.5f, -0.5f),
        Vec4(-0.5f, 0.5f, 0.5f),
        Vec4(-0.5f, 0.5f, -0.5f),
        Vec4(0.5f, -0.5f, 0.5f),
        Vec4(0.5f, -0.5f, -0.5f),
        Vec4(0.5f, 0.5f, 0.5f),
        Vec4(0.5f, 0.5f, -0.5f),
        )
    val edges = arrayOf(
        Pair(corners[0], corners[1]),
        Pair(corners[2], corners[3]),
        Pair(corners[4], corners[5]),
        Pair(corners[6], corners[7]),
        Pair(corners[0], corners[2]),
        Pair(corners[1], corners[3]),
        Pair(corners[4], corners[6]),
        Pair(corners[5], corners[7]),
        Pair(corners[0], corners[4]),
        Pair(corners[1], corners[5]),
        Pair(corners[2], corners[6]),
        Pair(corners[3], corners[7]),
    )
    init {
        assert(corners.size == 8)

    }



    private fun getCollidingEdgePoint(bb2: BoundingBox): Vec4?{
        for((p1,p2) in bb2.edges){
            // transform point so that it is positioned relative to this bbox which is axis aligned and in origin
            val p1 = t.inverseModelMatrix * p1
            // TODO: implement
            //if(isPointInThisBox(p)){
            //    return corner
            //}
        }
        return null
    }

    fun getCollidingCorner(bb2: BoundingBox): Vec4?{
        for((index, corner) in bb2.corners.withIndex()){
            // transform point so that it is positioned relative to this bbox which is axis aligned and in origin
            val p = t.inverseModelMatrix * corner
            if(isPointInThisBox(p)){
                bb2.onCornerCollisionCallback?.let {
                    it(index)
                }
                return corner
            }
        }
        return null
    }
    private fun isPointInThisBox(p: Vec4): Boolean{
        if(abs(p.x) >0.5){
            return false
        }
        if(abs(p.y) >0.5){
            return false
        }
        if(abs(p.z) >0.5){
            return false
        }
        return true
    }
}