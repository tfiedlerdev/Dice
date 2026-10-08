package tfdev.dice

import android.content.Context
import android.graphics.Color
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog

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

fun showDiceColorPicker(context: Context, onColorPicked: (Int) -> Unit) {
    val density = context.resources.displayMetrics.density
    val swatchSizePx = (44 * density).toInt()
    val marginPx = (6 * density).toInt()
    val grid = GridLayout(context).apply {
        columnCount = 4
        setPadding(marginPx * 2, marginPx * 2, marginPx * 2, marginPx * 2)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.title_pick_die_color)
        .setView(grid)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    for (presetColor in PRESET_DIE_COLORS) {
        val swatch = ColorSwatchView(context).apply {
            colorArgb = presetColor
            layoutParams = GridLayout.LayoutParams().apply {
                width = swatchSizePx
                height = swatchSizePx
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            setOnClickListener {
                onColorPicked(presetColor)
                dialog.dismiss()
            }
        }
        grid.addView(swatch)
    }
    dialog.show()
}
