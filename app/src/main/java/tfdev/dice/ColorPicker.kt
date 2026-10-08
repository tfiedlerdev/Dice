package tfdev.dice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow

/** A small fixed palette rather than a full HSV picker - plenty to tell dice apart, no custom color-math UI to build. */
private val PRESET_DIE_COLORS = intArrayOf(
    Color.rgb(244, 67, 54),
    Color.rgb(233, 30, 99),
    Color.rgb(156, 39, 176),
    Color.rgb(103, 58, 183),
    Color.rgb(63, 81, 181),
    Color.rgb(33, 150, 243),
    Color.rgb(0, 188, 212),
    Color.rgb(0, 150, 136),
    Color.rgb(76, 175, 80),
    Color.rgb(205, 220, 57),
    Color.rgb(255, 193, 7),
    Color.rgb(255, 152, 0),
    Color.rgb(121, 85, 72),
    Color.rgb(158, 158, 158),
    Color.rgb(242, 239, 230),
    Color.rgb(224, 224, 224)
)

// Noticeably lighter than the settings panel it floats over (#E6212121) - an elevated-
// surface gray, not just "a shade darker", so the card reads as its own distinct shape
// rather than nearly blending into the panel behind it.
private const val CARD_COLOR = 0xFF4A4A52.toInt()
private const val COLUMN_COUNT = 4
private const val CORNER_RADIUS_DP = 12f
private const val POINTER_WIDTH_DP = 16
private const val POINTER_HEIGHT_DP = 8
private const val SCREEN_EDGE_MARGIN_DP = 12

/** A small upward-pointing triangle in [pointerColor] - sits directly under the card so the two read as one shape. */
private class PointerView(context: Context, pointerColor: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pointerColor }
    override fun onDraw(canvas: Canvas) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(width.toFloat(), 0f)
            lineTo(width / 2f, height.toFloat())
            close()
        }
        canvas.drawPath(path, paint)
    }
}

/**
 * Shows the color palette as a small popover anchored to [anchor] (the die's own swatch
 * button), with a pointer triangle lined up with the anchor's center - a tooltip-style
 * popup rather than a modal dialog, so picking a color stays visually tied to the die it
 * applies to. Always opens above the anchor: in this app the dice-color row only ever
 * appears within the settings panel near the bottom of the screen, so there's reliably
 * room above it and never below.
 */
fun showDiceColorPicker(anchor: View, onColorPicked: (Int) -> Unit) {
    val context = anchor.context
    val density = context.resources.displayMetrics.density
    val swatchSizePx = (44 * density).toInt()
    val swatchMarginPx = (6 * density).toInt()
    val pointerWidthPx = (POINTER_WIDTH_DP * density).toInt()
    val pointerHeightPx = (POINTER_HEIGHT_DP * density).toInt()
    val cornerRadiusPx = CORNER_RADIUS_DP * density
    val edgeMarginPx = (SCREEN_EDGE_MARGIN_DP * density).toInt()

    lateinit var popupWindow: PopupWindow

    // Built as plain fixed-width rows rather than a GridLayout: GridLayout's own column-
    // width solving can under-measure (and so clip) its content when asked to measure
    // itself with an UNSPECIFIED spec, as the pre-measure below does - a row of fixed-
    // size children in a LinearLayout has no such ambiguity.
    val grid = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (8 * density).toInt()
        setPadding(pad, pad, pad, pad)
        for (rowColors in PRESET_DIE_COLORS.toList().chunked(COLUMN_COUNT)) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                // Explicit WRAP_CONTENT - a vertical LinearLayout's *implicit* default for
                // an added child is actually MATCH_PARENT width, and a MATCH_PARENT child
                // is specifically excluded from a LinearLayout's own wrap-content width
                // calculation (there's no final width yet for it to match) - left to that
                // default, every row measured 0 wide and the whole popup collapsed to just
                // its own padding, which read as a hard-clipped sliver of the leftmost
                // column once shown (this was the tooltip's width cut-off).
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                for (presetColor in rowColors) {
                    addView(ColorSwatchView(context).apply {
                        colorArgb = presetColor
                        layoutParams = LinearLayout.LayoutParams(swatchSizePx, swatchSizePx).apply {
                            setMargins(swatchMarginPx / 2, swatchMarginPx / 2, swatchMarginPx / 2, swatchMarginPx / 2)
                        }
                        setOnClickListener {
                            onColorPicked(presetColor)
                            popupWindow.dismiss()
                        }
                    })
                }
            })
        }
    }
    val card = LinearLayout(context).apply {
        // Explicit WRAP_CONTENT for the same reason as the row above: this is a child of
        // `content`, also a vertical LinearLayout, so it would otherwise default to
        // MATCH_PARENT and get excluded from content's own wrap-content width in turn.
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(grid)
        background = GradientDrawable().apply {
            setColor(CARD_COLOR)
            cornerRadius = cornerRadiusPx
        }
    }
    val pointer = PointerView(context, CARD_COLOR).apply {
        layoutParams = LinearLayout.LayoutParams(pointerWidthPx, pointerHeightPx)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(card)
        addView(pointer)
    }

    popupWindow = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
        isOutsideTouchable = true
        // Outside-touch dismissal only works reliably with a non-null background.
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            elevation = 12f * density
        }
    }

    // Measured up front (off-screen) so the popup's actual size is known before it's
    // positioned - needed to center it over the anchor and clamp it to the screen.
    content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val contentWidth = content.measuredWidth
    val contentHeight = content.measuredHeight

    val anchorLocation = IntArray(2)
    anchor.getLocationOnScreen(anchorLocation)
    val anchorCenterX = anchorLocation[0] + anchor.width / 2

    val screenWidth = context.resources.displayMetrics.widthPixels
    val popupLeft = (anchorCenterX - contentWidth / 2)
        .coerceIn(edgeMarginPx, (screenWidth - contentWidth - edgeMarginPx).coerceAtLeast(edgeMarginPx))
    val popupTop = anchorLocation[1] - contentHeight

    // The popup itself may have had to shift to stay on screen - keep the pointer
    // lined up with the anchor's actual center regardless, as long as it still fits
    // within the card's own rounded bounds.
    val maxPointerLeft = (contentWidth - pointerWidthPx - cornerRadiusPx.toInt()).coerceAtLeast(cornerRadiusPx.toInt())
    val pointerLeft = (anchorCenterX - popupLeft - pointerWidthPx / 2).coerceIn(cornerRadiusPx.toInt(), maxPointerLeft)
    pointer.layoutParams = (pointer.layoutParams as LinearLayout.LayoutParams).apply {
        leftMargin = pointerLeft
        gravity = Gravity.NO_GRAVITY
    }

    popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupLeft, popupTop)
}
