package tfdev.dice

import android.content.Context

/** Thin wrapper around the app's one SharedPreferences file, so settings survive a restart. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("dice_settings", Context.MODE_PRIVATE)

    fun getShakeSensitivity(default: Float) = prefs.getFloat(KEY_SHAKE_SENSITIVITY, default)
    fun setShakeSensitivity(value: Float) = prefs.edit().putFloat(KEY_SHAKE_SENSITIVITY, value).apply()

    fun getTiltEnabled(default: Boolean) = prefs.getBoolean(KEY_TILT_ENABLED, default)
    fun setTiltEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_TILT_ENABLED, value).apply()

    fun getRollHistoryEnabled(default: Boolean) = prefs.getBoolean(KEY_ROLL_HISTORY_ENABLED, default)
    fun setRollHistoryEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ROLL_HISTORY_ENABLED, value).apply()

    companion object {
        private const val KEY_SHAKE_SENSITIVITY = "shake_sensitivity"
        private const val KEY_TILT_ENABLED = "tilt_enabled"
        private const val KEY_ROLL_HISTORY_ENABLED = "roll_history_enabled"
    }
}
