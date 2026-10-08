package tfdev.dice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * A small square tile drawing one die face in 2D - the same conventional 1-6 pip layout
 * the 3D shader uses (see triangle.frag's pipMask), or a "?" when [faceValue] is null
 * (the die didn't come to rest level on a single face). Used by the roll-history overlay
 * and its expanded view; the dice-color swatches don't need pips, just [colorArgb].
 */
class DieFaceView(context: Context) : View(context) {
    var faceValue: Int? = null
        set(value) { field = value; invalidate() }

    var colorArgb: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 20, 20, 26) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(90, 0, 0, 0)
        strokeWidth = 2f
    }
    private val questionMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 20, 20, 26)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        backgroundPaint.color = colorArgb
        val cornerRadius = w * 0.18f
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val value = faceValue
        if (value == null) {
            questionMarkPaint.textSize = h * 0.6f
            val metrics = questionMarkPaint.fontMetrics
            val textY = h / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText("?", w / 2f, textY, questionMarkPaint)
            return
        }

        val radius = w * 0.09f
        fun pip(xFraction: Float, yFraction: Float) {
            canvas.drawCircle(w * xFraction, h * yFraction, radius, pipPaint)
        }
        when (value) {
            1 -> pip(0.5f, 0.5f)
            2 -> { pip(0.27f, 0.27f); pip(0.73f, 0.73f) }
            3 -> { pip(0.27f, 0.27f); pip(0.5f, 0.5f); pip(0.73f, 0.73f) }
            4 -> { pip(0.27f, 0.27f); pip(0.73f, 0.27f); pip(0.27f, 0.73f); pip(0.73f, 0.73f) }
            5 -> { pip(0.27f, 0.27f); pip(0.73f, 0.27f); pip(0.5f, 0.5f); pip(0.27f, 0.73f); pip(0.73f, 0.73f) }
            6 -> {
                pip(0.27f, 0.22f); pip(0.27f, 0.5f); pip(0.27f, 0.78f)
                pip(0.73f, 0.22f); pip(0.73f, 0.5f); pip(0.73f, 0.78f)
            }
        }
    }
}
