package tfdev.dice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The roll-history feed: oldest entry at the top, newest at the bottom, in a plain
 * scrollable list rather than a separate "compact feed" that has to be tapped open into
 * a full view - the whole session is already right there, just scroll. Normally
 * auto-scrolled to the bottom so a new result is always revealed as it comes in (older
 * ones correspondingly scroll up out of view). A manual drag suspends that until either
 * the user scrolls back down to the bottom themselves, or - if they just leave it
 * scrolled away - [RETURN_TO_BOTTOM_DELAY_MILLIS] of inactivity passes and it springs
 * back on its own, so the feed never permanently gets stuck showing stale history.
 */
class RollHistoryOverlay(
    private val context: Context,
    private val scrollView: ScrollView,
    private val container: LinearLayout
) {
    private var autoScrollToBottom = true
    private val returnToBottomHandler = Handler(Looper.getMainLooper())
    private val returnToBottomRunnable = Runnable {
        autoScrollToBottom = true
        scrollView.smoothScrollTo(0, maxScrollY())
    }

    init {
        // Returning false leaves the touch free for the ScrollView's own handling - this
        // is just to notice "the user is dragging" and suspend auto-scroll while they are.
        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    autoScrollToBottom = false
                    returnToBottomHandler.removeCallbacks(returnToBottomRunnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!autoScrollToBottom) {
                        returnToBottomHandler.postDelayed(returnToBottomRunnable, RETURN_TO_BOTTOM_DELAY_MILLIS)
                    }
                }
            }
            false
        }
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (scrollView.scrollY >= maxScrollY()) {
                autoScrollToBottom = true
                returnToBottomHandler.removeCallbacks(returnToBottomRunnable)
            }
        }
    }

    private fun maxScrollY(): Int {
        val content = scrollView.getChildAt(0) ?: return 0
        return (content.height - scrollView.height).coerceAtLeast(0)
    }

    fun addEntry(entry: RollEntry) {
        val row = buildRow(entry).apply {
            alpha = 0f
            translationY = context.resources.displayMetrics.density * 24f
        }
        container.addView(row)
        row.animate().alpha(1f).translationY(0f).setDuration(ENTRY_ANIMATION_MILLIS).start()

        if (autoScrollToBottom) {
            // Posted so it runs after the new row has actually been laid out - scrolling
            // to "the bottom" before that would use the old (shorter) content height.
            scrollView.post { scrollView.smoothScrollTo(0, maxScrollY()) }
        }
    }

    /** Clears the whole session's history - call on Reset. */
    fun clear() {
        container.removeAllViews()
        autoScrollToBottom = true
        returnToBottomHandler.removeCallbacks(returnToBottomRunnable)
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
        private const val ENTRY_ANIMATION_MILLIS = 250L
        private const val RETURN_TO_BOTTOM_DELAY_MILLIS = 10_000L
    }
}
