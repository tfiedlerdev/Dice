package tfdev.dice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** A plain colored circle - used both for a die's own color indicator and for the preset palette to pick from. */
class ColorSwatchView(context: Context) : View(context) {
    var colorArgb: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(140, 0, 0, 0)
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        val radius = (minOf(width, height) / 2f) - 2f
        if (radius <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        fillPaint.color = colorArgb
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }
}
