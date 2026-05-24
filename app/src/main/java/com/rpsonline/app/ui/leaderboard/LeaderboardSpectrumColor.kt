package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Even split for rock / paper / scissors — anchor for the mid (yellow-green) point. */
private const val BalancedSharePercent = 33.333f

private const val RedHue = 6f
private const val YellowHue = 52f
private const val GreenHue = 136f

/** Below ~33%: stay red longer before easing to yellow. Above: shift to green quickly. */
private const val LowSegmentGamma = 1.4f
private const val HighSegmentGamma = 0.68f

private fun lerpHue(from: Float, to: Float, fraction: Float): Float {
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return (from + delta * fraction + 360f) % 360f
}

/**
 * Smooth red → yellow → green spectrum.
 * [percent] is 0–100; ~33% is yellow. Low shares stay red; high shares lean green.
 */
@Composable
fun leaderboardSpectrumColor(percent: Float): Color {
    val p = percent.coerceIn(0f, 100f)
    val dark = isSystemInDarkTheme()
    val baseSaturation = if (dark) 0.9f else 0.96f
    val baseLightness = if (dark) 0.52f else 0.38f

    val hue = when {
        p <= BalancedSharePercent -> {
            val t = if (BalancedSharePercent > 0f) p / BalancedSharePercent else 0f
            lerpHue(RedHue, YellowHue, t.pow(LowSegmentGamma))
        }
        else -> {
            val span = 100f - BalancedSharePercent
            val t = if (span > 0f) (p - BalancedSharePercent) / span else 1f
            lerpHue(YellowHue, GreenHue, t.pow(HighSegmentGamma))
        }
    }

    val saturation = when {
        hue <= 25f -> baseSaturation.coerceAtLeast(0.97f)
        hue in 45f..150f -> (baseSaturation + 0.04f).coerceAtMost(1f)
        else -> baseSaturation
    }

    return Color.hsl(hue, saturation, baseLightness)
}
