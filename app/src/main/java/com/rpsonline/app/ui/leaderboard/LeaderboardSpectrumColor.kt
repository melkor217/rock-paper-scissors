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
fun leaderboardSpectrumColor(percent: Float, darkTheme: Boolean): Color {
    val p = percent.coerceIn(0f, 100f)
    val baseSaturation = if (darkTheme) 0.9f else 0.52f
    val baseLightness = if (darkTheme) 0.52f else 0.4f

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

    val saturation = if (darkTheme) {
        when {
            hue <= 25f -> baseSaturation.coerceAtLeast(0.97f)
            hue in 45f..150f -> (baseSaturation + 0.04f).coerceAtMost(1f)
            else -> baseSaturation
        }
    } else {
        when {
            hue <= 25f -> (baseSaturation + 0.06f).coerceAtMost(0.58f)
            hue in 45f..150f -> (baseSaturation + 0.1f).coerceAtMost(0.62f)
            else -> baseSaturation
        }
    }

    return Color.hsl(hue, saturation, baseLightness)
}

@Composable
fun leaderboardSpectrumColor(percent: Float): Color =
    leaderboardSpectrumColor(percent, isSystemInDarkTheme())

/** Fewer throws per win is better; 2 green → 7.5 yellow → 13 red. */
private const val RpsPerWinBest = 2f
private const val RpsPerWinMid = 7.5f
private const val RpsPerWinWorst = 13f

fun rpsPerWinColor(throwsPerWin: Double, darkTheme: Boolean): Color {
    val value = throwsPerWin.toFloat().coerceIn(RpsPerWinBest, RpsPerWinWorst)
    val percent = when {
        value <= RpsPerWinMid -> {
            val span = RpsPerWinMid - RpsPerWinBest
            val t = if (span > 0f) (value - RpsPerWinBest) / span else 0f
            (1f - t) * 100f + t * BalancedSharePercent
        }
        else -> {
            val span = RpsPerWinWorst - RpsPerWinMid
            val t = if (span > 0f) (value - RpsPerWinMid) / span else 1f
            BalancedSharePercent * (1f - t)
        }
    }
    return leaderboardSpectrumColor(percent, darkTheme)
}

@Composable
fun rpsPerWinColor(throwsPerWin: Double): Color =
    rpsPerWinColor(throwsPerWin, isSystemInDarkTheme())
