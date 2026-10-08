package tfdev.dice

import android.content.Context
import android.widget.LinearLayout

/** The horizontal "Dice colors" row in settings - one swatch per die, newest at the start. */
class DiceColorRow(
    private val context: Context,
    private val container: LinearLayout
) {
    /** Inserts a swatch for [entry] at the start of the row - call for a newly-added die. */
    fun addDieAtStart(entry: DieEntry) {
        val density = context.resources.displayMetrics.density
        val sizePx = (44 * density).toInt()
        val marginPx = (6 * density).toInt()
        val swatch = ColorSwatchView(context).apply {
            colorArgb = entry.colorArgb
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginStart = marginPx
                marginEnd = marginPx
            }
            setOnClickListener {
                showDiceColorPicker(context) { pickedColor ->
                    entry.colorArgb = pickedColor
                    entry.die.setColor(argbToVec4(pickedColor))
                    colorArgb = pickedColor
                }
            }
        }
        container.addView(swatch, 0)
    }

    /** Rebuilds the row from scratch to match [entries] (newest-first) - call after a reset. */
    fun rebuild(entries: List<DieEntry>) {
        container.removeAllViews()
        // Insert oldest-first, since each insertion goes to the front - the last one
        // inserted (the actual newest) ends up at the front, matching entries' own order.
        for (entry in entries.asReversed()) {
            addDieAtStart(entry)
        }
    }
}
