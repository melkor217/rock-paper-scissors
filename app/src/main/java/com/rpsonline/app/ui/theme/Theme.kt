package com.rpsonline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF1B5E20)
private val GreenLight = Color(0xFF81C784)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    secondary = GreenDark,
    tertiary = GreenLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color.Black,
    secondary = GreenPrimary,
    tertiary = GreenDark,
)

@Composable
fun RpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
