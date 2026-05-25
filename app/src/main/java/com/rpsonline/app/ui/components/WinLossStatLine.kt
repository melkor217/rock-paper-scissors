package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.rpsonline.app.ui.leaderboard.leaderboardSpectrumColor

@Composable
fun WinLossStatLine(
    wins: Int,
    losses: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val games = wins + losses
    val winRate = games.takeIf { it > 0 }?.let { (wins * 100) / it }
    val boldStyle = textStyle.copy(fontWeight = FontWeight.Bold)
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "W $wins",
            style = boldStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = " · ",
            style = textStyle,
            color = separatorColor,
        )
        Text(
            text = "L $losses",
            style = boldStyle,
            color = MaterialTheme.colorScheme.error,
        )
        winRate?.let { rate ->
            Text(
                text = " · ",
                style = textStyle,
                color = separatorColor,
            )
            Text(
                text = "$rate%",
                style = boldStyle,
                color = leaderboardSpectrumColor(rate.toFloat()),
            )
        }
    }
}
