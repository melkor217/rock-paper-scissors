package com.rpsonline.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.rpsonline.app.data.preferences.AppThemeStyle

val LocalAppThemeStyle = compositionLocalOf { AppThemeStyle.default }

/** App theme darkness — follows the selected style, not the system setting. */
val LocalRpsDarkTheme = compositionLocalOf { true }

@Composable
fun isRpsDarkTheme(): Boolean = LocalRpsDarkTheme.current

@Composable
fun currentAppThemeStyle(): AppThemeStyle = LocalAppThemeStyle.current
