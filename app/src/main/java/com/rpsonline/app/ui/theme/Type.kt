package com.rpsonline.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Mono = FontFamily.Monospace
private val Display = FontFamily.SansSerif

private fun cyberStyle(
    base: TextStyle,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0.5f,
): TextStyle = base.copy(
    fontFamily = if (base.fontSize >= 22.sp) Display else Mono,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
)

val CyberpunkTypography = Typography(
    displayLarge = cyberStyle(
        TextStyle(fontSize = 57.sp, lineHeight = 64.sp),
        FontWeight.Bold,
        1.5f,
    ),
    displayMedium = cyberStyle(
        TextStyle(fontSize = 45.sp, lineHeight = 52.sp),
        FontWeight.Bold,
        1.2f,
    ),
    displaySmall = cyberStyle(
        TextStyle(fontSize = 36.sp, lineHeight = 44.sp),
        FontWeight.Bold,
        1f,
    ),
    headlineLarge = cyberStyle(
        TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
        FontWeight.Bold,
        0.8f,
    ),
    headlineMedium = cyberStyle(
        TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
        FontWeight.SemiBold,
        0.6f,
    ),
    headlineSmall = cyberStyle(
        TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
        FontWeight.SemiBold,
        0.5f,
    ),
    titleLarge = cyberStyle(
        TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
        FontWeight.SemiBold,
        0.4f,
    ),
    titleMedium = cyberStyle(
        TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        FontWeight.Medium,
        0.3f,
    ),
    titleSmall = cyberStyle(
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        FontWeight.Medium,
        0.25f,
    ),
    bodyLarge = cyberStyle(TextStyle(fontSize = 16.sp, lineHeight = 24.sp)),
    bodyMedium = cyberStyle(TextStyle(fontSize = 14.sp, lineHeight = 20.sp)),
    bodySmall = cyberStyle(TextStyle(fontSize = 12.sp, lineHeight = 16.sp)),
    labelLarge = cyberStyle(
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        FontWeight.SemiBold,
        1f,
    ),
    labelMedium = cyberStyle(
        TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        FontWeight.Medium,
        0.8f,
    ),
    labelSmall = cyberStyle(
        TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
        FontWeight.Medium,
        1.2f,
    ),
)
