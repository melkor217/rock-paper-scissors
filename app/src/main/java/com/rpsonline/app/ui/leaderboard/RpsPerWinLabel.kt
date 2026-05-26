package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.ui.components.MoveStatIconSize
import com.rpsonline.app.ui.components.ThrowCountRow
import com.rpsonline.app.ui.theme.currentAppThemeStyle
import java.util.Locale

fun throwsPerWin(
    wins: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
): Double? {
    if (wins <= 0) return null
    val totalThrows = throwsRock + throwsPaper + throwsScissors
    if (totalThrows <= 0) return null
    return totalThrows.toDouble() / wins
}

fun hasThrowStats(wins: Int, throwsRock: Int, throwsPaper: Int, throwsScissors: Int): Boolean =
    wins > 0 && throwsRock + throwsPaper + throwsScissors > 0

fun LeaderboardEntry.throwsPerWin(): Double? =
    throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors)

fun UserProfile.throwsPerWin(): Double? =
    throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors)

fun formatThrowsPerWin(value: Double): String =
    String.format(Locale.US, "%.1f", value)

@Composable
fun RpsPerWinLabel(
    throwsPerWin: Double,
    modifier: Modifier = Modifier,
    iconSize: Dp = MoveStatIconSize,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = if (currentAppThemeStyle() == AppThemeStyle.CYBERPUNK) {
        MaterialTheme.colorScheme.primary
    } else {
        rpsPerWinColor(throwsPerWin)
    },
    showMoveIcons: Boolean = true,
) {
    val valueStyle = textStyle.copy(fontWeight = FontWeight.Bold)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatThrowsPerWin(throwsPerWin),
            style = valueStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showMoveIcons) {
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(iconSize),
                )
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(iconSize),
                )
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "/Win",
            style = textStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlayerThrowStatsColumn(
    wins: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    if (!hasThrowStats(wins, throwsRock, throwsPaper, throwsScissors)) return
    val throwsPerWin = throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors) ?: return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RpsPerWinLabel(
            throwsPerWin = throwsPerWin,
            textStyle = textStyle,
            showMoveIcons = true,
        )
        ThrowCountRow(
            rock = throwsRock,
            paper = throwsPaper,
            scissors = throwsScissors,
            textStyle = textStyle,
            iconSize = MoveStatIconSize,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        )
    }
}
