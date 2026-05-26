package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.ui.leaderboard.leaderboardSpectrumColor
import com.rpsonline.app.ui.theme.currentAppThemeStyle

@Composable
fun WinLossStatLine(
    wins: Int,
    losses: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val games = wins + losses
    val winRate = games.takeIf { it > 0 }?.let { (wins * 100) / it }
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isCyberpunk = currentAppThemeStyle() == AppThemeStyle.CYBERPUNK
    val winRateColor = if (isCyberpunk) {
        MaterialTheme.colorScheme.tertiary
    } else {
        winRate?.let { leaderboardSpectrumColor(it.toFloat()) } ?: MaterialTheme.colorScheme.tertiary
    }
    val boldWeight = FontWeight.Bold
    val fontSize = textStyle.fontSize
    val fontFamily = textStyle.fontFamily
    val letterSpacing = textStyle.letterSpacing

    val line = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = boldWeight,
                fontSize = fontSize,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
            ),
        ) {
            append("W $wins")
        }
        withStyle(
            SpanStyle(
                color = separatorColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
            ),
        ) {
            append(" / ")
        }
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.error,
                fontWeight = boldWeight,
                fontSize = fontSize,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
            ),
        ) {
            append("L $losses")
        }
        if (winRate != null) {
            withStyle(
                SpanStyle(
                    color = separatorColor,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    letterSpacing = letterSpacing,
                ),
            ) {
                append(" / ")
            }
            withStyle(
                SpanStyle(
                    color = winRateColor,
                    fontWeight = boldWeight,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    letterSpacing = letterSpacing,
                ),
            ) {
                append("$winRate%")
            }
        }
    }

    Text(
        text = line,
        modifier = modifier,
        style = textStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}
