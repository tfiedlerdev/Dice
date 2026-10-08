package tfdev.dice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog

/**
 * Manages the "latest rolls" overlay: a compact, transparent-background stack of recent
 * results (newest on top) that fade out after [FADE_AFTER_MILLIS] - except whichever is
 * still the latest, which stays up regardless of age - plus a full scrollable history of
 * every roll since the last reset, shown on tap or swipe (see [showExpandedHistory]).
 */
class RollHistoryOverlay(
    private val context: Context,
    private val compactContainer: LinearLayout
) {
    private val allEntries = mutableListOf<RollEntry>()
    private val fadeHandler = Handler(Looper.getMainLooper())
    private var expandedDialog: AlertDialog? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showExpandedHistory()
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            showExpandedHistory()
            return true
        }
    })

    init {
        compactContainer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
    }

    fun addEntry(entry: RollEntry) {
        allEntries.add(entry)
        val row = buildRow(entry, tileSizeDp = 36)
        compactContainer.addView(row, 0)
        fadeHandler.postDelayed({ checkFade(row) }, FADE_AFTER_MILLIS)
    }

    /** Clears both the compact feed and the full session history - call on Reset. */
    fun clear() {
        allEntries.clear()
        compactContainer.removeAllViews()
        expandedDialog?.dismiss()
    }

    fun setEnabled(enabled: Boolean) {
        compactContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) expandedDialog?.dismiss()
    }

    private fun checkFade(row: View) {
        if (compactContainer.indexOfChild(row) == 0) {
            // Still the latest entry - exempt from fading; just check back later.
            fadeHandler.postDelayed({ checkFade(row) }, FADE_RECHECK_MILLIS)
            return
        }
        row.animate().alpha(0f).setDuration(FADE_DURATION_MILLIS).withEndAction {
            compactContainer.removeView(row)
        }.start()
    }

    private fun buildRow(entry: RollEntry, tileSizeDp: Int): LinearLayout {
        val density = context.resources.displayMetrics.density
        val tileSizePx = (tileSizeDp * density).toInt()
        val marginPx = (3 * density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, marginPx * 2)
            for (result in entry.dieResults) {
                addView(DieFaceView(context).apply {
                    faceValue = result.faceValue
                    colorArgb = result.colorArgb
                    layoutParams = LinearLayout.LayoutParams(tileSizePx, tileSizePx).apply { marginEnd = marginPx }
                })
            }
        }
    }

    private fun showExpandedHistory() {
        if (allEntries.isEmpty() || expandedDialog?.isShowing == true) return
        val density = context.resources.displayMetrics.density
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val paddingPx = (16 * density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            for (entry in allEntries.asReversed()) {
                addView(buildRow(entry, tileSizeDp = 44))
            }
        }
        expandedDialog = AlertDialog.Builder(context)
            .setTitle(R.string.title_roll_history)
            .setView(ScrollView(context).apply { addView(listLayout) })
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { expandedDialog = null }
            .show()
    }

    companion object {
        private const val FADE_AFTER_MILLIS = 10_000L
        private const val FADE_RECHECK_MILLIS = 1_000L
        private const val FADE_DURATION_MILLIS = 400L
    }
}
