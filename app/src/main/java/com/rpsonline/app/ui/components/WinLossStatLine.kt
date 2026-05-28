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
import com.rpsonline.app.ui.leaderboard.leaderboardWinRateColor
import com.rpsonline.app.ui.leaderboard.leaderboardSpectrumColor
import com.rpsonline.app.ui.leaderboard.winRatePercent
import com.rpsonline.app.ui.theme.currentAppThemeStyle

@Composable
fun WinLossStatLine(
    wins: Int,
    losses: Int,
    draws: Int = 0,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val winRate = winRatePercent(wins, losses, draws)
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isCyberpunk = currentAppThemeStyle() == AppThemeStyle.CYBERPUNK
    val winRateColor = if (isCyberpunk) {
        MaterialTheme.colorScheme.tertiary
    } else {
        winRate?.let { leaderboardSpectrumColor(it.toFloat()) } ?: MaterialTheme.colorScheme.tertiary
    }
    val fontSize = textStyle.fontSize
    val fontFamily = textStyle.fontFamily
    val letterSpacing = textStyle.letterSpacing

    val line = buildAnnotatedString {
        appendStatToken(
            label = "W",
            value = wins.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = fontSize,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
        )
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
        appendStatToken(
            label = "L",
            value = losses.toString(),
            color = MaterialTheme.colorScheme.error,
            fontSize = fontSize,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
        )
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
        appendStatToken(
            label = "D",
            value = draws.toString(),
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = fontSize,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
        )
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
                    fontWeight = FontWeight.Bold,
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

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStatToken(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    letterSpacing: androidx.compose.ui.unit.TextUnit,
) {
    withStyle(
        SpanStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
        ),
    ) {
        append("$label ")
    }
    withStyle(
        SpanStyle(
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
        ),
    ) {
        append(value)
    }
}

@Composable
fun RoundWinRateLine(
    wins: Int,
    losses: Int,
    draws: Int = 0,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    label: String = "Round WR: ",
) {
    val roundWinRate = winRatePercent(wins = wins, losses = losses, draws = draws)
    val valueText = roundWinRate?.let { "$it%" } ?: "-"
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(label)
            }
            withStyle(
                SpanStyle(
                    color = leaderboardWinRateColor(roundWinRate),
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(valueText)
            }
        },
        modifier = modifier,
        style = textStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
