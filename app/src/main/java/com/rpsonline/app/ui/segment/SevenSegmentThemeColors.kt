package com.rpsonline.app.ui.segment

import android.content.Context
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.ui.theme.colorSchemeFor

/** Theme-aware seven-segment colors for notifications and other non-Compose surfaces. */
object SevenSegmentThemeColors {
    fun paletteFor(context: Context): SevenSegmentPalette {
        return paletteFor(ThemePreferences(context).get())
    }

    fun paletteFor(style: AppThemeStyle): SevenSegmentPalette {
        val scheme = colorSchemeFor(style)
        val isDark = style.isDark
        val ghost = if (isDark) {
            lerp(
                lerp(scheme.surfaceContainerLow, scheme.surface, 0.38f),
                scheme.outlineVariant,
                0.06f,
            )
        } else {
            lerp(scheme.surfaceContainerHigh, scheme.outlineVariant, 0.36f)
                .copy(alpha = 0.87f)
        }
        val lit = if (isDark) {
            lerp(scheme.primary, scheme.onPrimaryContainer, 0.48f)
        } else {
            lerp(scheme.primary, scheme.onPrimaryContainer, 0.12f)
        }
        val halfLit = lerp(ghost, lit, 0.58f)
        return SevenSegmentPalette(
            litArgb = lit.toArgb(),
            ghostArgb = ghost.toArgb(),
            halfLitArgb = halfLit.toArgb(),
        )
    }
}
