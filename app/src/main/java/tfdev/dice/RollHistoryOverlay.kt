package tfdev.dice

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The roll-history feed: newest entry at the top, oldest at the bottom - sitting
 * bottom-right, spanning the same height as the 3D view (see the layout). A runtime top
 * padding on the content keeps it resting with only the latest entry plus a sliver of
 * the next one peeking just above the controls bar by default - a hint that there's
 * more, without a fixed-size box competing with the 3D view for space of its own. The
 * rest of the viewport above that padding is empty, and the 3D view shows through it.
 *
 * Normally auto-scrolled back to the top so a new result is always the one on top and
 * fully shown; a manual drag suspends that until the user scrolls back to the top
 * themselves.
 */
class RollHistoryOverlay(
    private val context: Context,
    private val scrollView: ScrollView,
    private val container: LinearLayout
) {
    private var autoScrollToTop = true
    private val containerBasePaddingLeft = container.paddingLeft
    private val containerBasePaddingRight = container.paddingRight
    private val containerBasePaddingBottom = container.paddingBottom

    init {
        // Returning false leaves the touch free for the ScrollView's own handling - this
        // is just to notice "the user is dragging" and suspend auto-scroll while they are.
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                autoScrollToTop = false
            }
            false
        }
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (scrollView.scrollY <= 0) {
                autoScrollToTop = true
            }
        }
        // The viewport's height (and so the top padding "only the latest" needs) isn't
        // known until after layout, and can change (e.g. a rotation).
        scrollView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                updateTopPadding()
            }
        }
    }

    private fun updateTopPadding() {
        val viewportHeightPx = scrollView.height
        if (viewportHeightPx <= 0) return // not laid out yet - the layout-change callback will follow up
        val density = context.resources.displayMetrics.density
        val visibleContentPx = (ROW_HEIGHT_DP * VISIBLE_ROWS * density).toInt()
        val topPaddingPx = (viewportHeightPx - visibleContentPx).coerceAtLeast(0)
        container.setPadding(containerBasePaddingLeft, topPaddingPx, containerBasePaddingRight, containerBasePaddingBottom)
    }

    fun addEntry(entry: RollEntry) {
        val row = buildRow(entry).apply {
            alpha = 0f
            translationY = -context.resources.displayMetrics.density * 24f
        }
        container.addView(row, 0)
        row.animate().alpha(1f).translationY(0f).setDuration(ENTRY_ANIMATION_MILLIS).start()

        if (autoScrollToTop) {
            // Posted so it runs after the new row has actually been laid out.
            scrollView.post { scrollView.smoothScrollTo(0, 0) }
        }
    }

    /** Clears the whole session's history - call on Reset. */
    fun clear() {
        container.removeAllViews()
        autoScrollToTop = true
    }

    fun setEnabled(enabled: Boolean) {
        scrollView.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun buildRow(entry: RollEntry): LinearLayout {
        val density = context.resources.displayMetrics.density
        val tileSizePx = (TILE_SIZE_DP * density).toInt()
        val marginPx = (3 * density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // Match the content column's own (widest-row-driven) width and pack this
            // row's dice against its end - otherwise a row with fewer dice than the
            // widest one just sits at the start, off-center from the screen edge the
            // rest of the feed hugs.
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = marginPx * 2 }
            for (result in entry.dieResults) {
                addView(DieFaceView(context).apply {
                    faceValue = result.faceValue
                    colorArgb = result.colorArgb
                    layoutParams = LinearLayout.LayoutParams(tileSizePx, tileSizePx).apply { marginEnd = marginPx }
                })
            }
        }
    }

    companion object {
        private const val TILE_SIZE_DP = 36

        /** A row's rendered height: the tile plus its own top margin (see buildRow). */
        private const val ROW_HEIGHT_DP = TILE_SIZE_DP + 6

        /** How many rows show by default above the controls bar - see the class doc. */
        private const val VISIBLE_ROWS = 1.5f

        private const val ENTRY_ANIMATION_MILLIS = 250L
    }
}
