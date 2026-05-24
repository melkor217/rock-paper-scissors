package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Even split for rock / paper / scissors — anchor for yellow on the spectrum. */
private const val BalancedSharePercent = 33.333f

private const val RedHue = 4f
private const val YellowHue = 48f
private const val GreenHue = 128f

private fun lerpHue(from: Float, to: Float, fraction: Float): Float {
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return (from + delta * fraction + 360f) % 360f
}

/**
 * Smooth red → yellow → green spectrum.
 * [percent] is 0–100; ~33% is yellow, lower is redder, higher is greener.
 */
@Composable
fun leaderboardSpectrumColor(percent: Float): Color {
    val p = percent.coerceIn(0f, 100f)
    val dark = isSystemInDarkTheme()
    // Higher saturation + slightly lower lightness so thin chart strokes stay vivid on pale cards.
    val baseSaturation = if (dark) 0.9f else 0.96f
    val baseLightness = if (dark) 0.52f else 0.38f

    val hue = when {
        p <= BalancedSharePercent -> {
            val t = if (BalancedSharePercent > 0f) p / BalancedSharePercent else 0f
            lerpHue(RedHue, YellowHue, t)
        }
        else -> {
            val span = 100f - BalancedSharePercent
            val t = if (span > 0f) (p - BalancedSharePercent) / span else 1f
            lerpHue(YellowHue, GreenHue, t)
        }
    }

    // Yellow–green hues read dull at equal S/L; nudge saturation up in that band.
    val midBandBoost = when {
        hue in 35f..140f -> 0.04f
        else -> 0f
    }
    val saturation = (baseSaturation + midBandBoost).coerceAtMost(1f)

    return Color.hsl(hue, saturation, baseLightness)
}
