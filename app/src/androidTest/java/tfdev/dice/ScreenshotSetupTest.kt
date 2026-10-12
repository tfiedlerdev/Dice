package tfdev.dice

import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dev utility for capturing Play Store marketing screenshots on an emulator - NOT a
 * regression test. Seeds a colorful field of dice (showing off per-die color
 * customization) and backfills a varied roll-history feed (showing off the "peek the
 * next entry" default view and the right-aligned multi-die rows, see
 * RollHistoryOverlay), then holds the activity open for [holdOpenMillis] so screenshots
 * can be taken manually via `adb exec-out screencap`.
 *
 * Guarded by `assumeTrue` on the "screenshotMode" arg so a normal `connectedDebugAndroidTest`
 * run (which picks up every androidTest class) skips this instantly instead of hanging for
 * the full hold-open duration.
 *
 * Unlike some other apps, this one has no screenshot-blocking window flag and no dynamic/
 * wallpaper-driven theming to work around first - seeding is the only setup needed. Its UI
 * strings follow the device's own locale (see the per-locale values-xx resource folders), not an
 * instrumentation arg, so for a localized screenshot batch change the emulator's system
 * language before capturing rather than passing anything to this test.
 *
 * Full recipe to capture a fresh batch of screenshots:
 * 1. `./gradlew assembleDebug assembleDebugAndroidTest`, install both APKs.
 * 2. `adb shell am instrument -w -e class tfdev.dice.ScreenshotSetupTest -e screenshotMode
 *    true tfdev.dice.test/androidx.test.runner.AndroidJUnitRunner` (run in the background -
 *    it blocks for [holdOpenMillis]).
 * 3. Give the dice a second or two to actually land (they're dropped fresh, same as a real
 *    "Add Die" tap) - the seeded scene is the hero shot already: a colorful field plus a
 *    history feed peeking the latest (also colorful) roll. For the other screens, tap
 *    through manually and screenshot each as you go:
 *    - Tap "Settings" for the shake-sensitivity/tilt/history controls and the dice-color
 *      swatches row.
 *    - Tap a die-color swatch there for the pointing tooltip color picker.
 *    - Drag the roll-history feed up to show the full session list rather than just the
 *      default peek.
 *    `adb exec-out screencap -p > shot.png` for each.
 * 4. Shake the device/emulator for an actual mid-tumble action shot, if you want one - the
 *    seeded history keeps recording real rolls on top of the seeded ones.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotSetupTest {

    private val holdOpenMillis = 20 * 60 * 1000L

    @Test
    fun seedDataAndHoldOpen() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Only runs when explicitly requested - see class doc for the invocation recipe.",
            args.getString("screenshotMode") != null
        )

        // A vibrant, mutually distinct subset of the color picker's own palette (see
        // ColorPicker.kt) - skips the near-duplicate/neutral entries (gray, off-white)
        // that don't read as clearly different colors at thumbnail size.
        val red = Color.rgb(244, 67, 54)
        val blue = Color.rgb(33, 150, 243)
        val green = Color.rgb(76, 175, 80)
        val orange = Color.rgb(255, 152, 0)
        val purple = Color.rgb(156, 39, 176)
        val teal = Color.rgb(0, 150, 136)
        val dieColors = listOf(red, blue, green, orange, purple, teal)

        // Oldest first - the last entry here ends up newest (on top, fully shown by
        // default), and matches the colors of the dice actually left on the field, so the
        // peeked history entry reads as "this is the roll you're looking at right now".
        val historyRollColors = listOf(
            listOf(red),
            listOf(green, orange, purple),
            listOf(blue, teal),
            dieColors
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.seedForScreenshots(dieColors, historyRollColors)
            }
            // Keep the process alive so screenshots can be taken manually via adb while
            // this instrumented test session holds the activity open.
            Thread.sleep(holdOpenMillis)
        }
    }
}
