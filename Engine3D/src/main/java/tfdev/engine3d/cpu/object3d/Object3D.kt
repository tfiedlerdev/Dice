package sensors_in_paradise.sonar.custom_views.stickman.object3d

import android.graphics.*
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.Transform
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

abstract class Object3D(
    protected val vertices: Array<Vec4>
): Transform() {
    var parent: Object3D? = null
    private val defaultVertices = vertices.map { it.clone() }
    var drawVertexPositionsForDebugging = false
    private val vertexPositionsDebuggingHeader = "i_|_x__|_y__|_z__"
    private val debugTextBounds = Rect().apply {
        debugTextPaint.getTextBounds(
            vertexPositionsDebuggingHeader,
            0,
            vertexPositionsDebuggingHeader.length,
            this
        )
    }
    protected val children: ArrayList<Object3D> = ArrayList()


    fun addChild(obj: Object3D){
        obj.parent = this
        children.add(obj)
    }

    fun updateSelfAndChild(){
        if(!isDirty){
            return
        }
        forceUpdateSelfAndChild()
    }
    fun forceUpdateSelfAndChild(){
        val parent = this.parent
        if(parent==null){
            computeModelMatrix()
        }
        else{
            computeModelMatrix(parent.modelMatrix)
        }
        for(child in children){
            child.forceUpdateSelfAndChild()
        }
    }


    private fun radiansToDegrees(radians: Float): Float {
        return (radiansToDegreesFactor * radians)
    }



    private fun drawVertexPositionsForDebugging(canvas: Canvas) {
        var y = 70f
        canvas.drawText(
            vertexPositionsDebuggingHeader,
            canvas.width - 10f - debugTextBounds.width(),
            y,
            debugTextPaint
        )
        val f = { x: Float -> String.format(Locale.US, "%.2f", x) }
        val i = { i: Int -> i.toString().padStart(2, ' ') }

        for (index in 0 until min(vertices.size, 99)) {
            val v = vertices[index]
            y += debugTextBounds.height()
            canvas.drawText(
                "${i(index)}|${f(v.x)}|${f(v.y)}|${f(v.z)}",
                canvas.width - 10f - debugTextBounds.width(),
                y,
                debugTextPaint
            )
        }
    }

    fun draw(canvas: Canvas, projectPoint: (p: Vec4) -> PointF) {
        if (drawVertexPositionsForDebugging) {
            drawVertexPositionsForDebugging(canvas)
        }
        updateSelfAndChild()
        drawSelf(canvas, projectPoint)

        for (child in children) {

            child.draw(canvas, projectPoint)
        }
    }

    protected abstract fun drawSelf(canvas: Canvas, projectPoint: (p: Vec4) -> PointF)

    companion object {
        private const val radiansToDegreesFactor = Math.PI.toFloat() / 180f
        private val debugTextPaint = Paint(0).apply {
            color = Color.WHITE
        }
    }
}
