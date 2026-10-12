package tfdev.dice

import android.graphics.Color
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4

fun argbToVec4(argb: Int): Vec4 = Vec4(
    Color.red(argb) / 255f,
    Color.green(argb) / 255f,
    Color.blue(argb) / 255f,
    1f
)

fun vec4ToArgb(color: Vec4): Int = Color.rgb(
    (color.x * 255f).toInt().coerceIn(0, 255),
    (color.y * 255f).toInt().coerceIn(0, 255),
    (color.z * 255f).toInt().coerceIn(0, 255)
)
