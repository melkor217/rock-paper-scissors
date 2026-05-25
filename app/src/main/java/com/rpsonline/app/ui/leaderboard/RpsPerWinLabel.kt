package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.ui.components.MoveStatIconSize
import com.rpsonline.app.data.model.UserProfile
import kotlin.math.roundToInt

private fun throwsPerWin(
    wins: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
): Double? {
    if (wins <= 0) return null
    val totalThrows = throwsRock + throwsPaper + throwsScissors
    return totalThrows.toDouble() / wins
}

fun LeaderboardEntry.throwsPerWin(): Double? =
    throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors)

fun UserProfile.throwsPerWin(): Double? =
    throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors)

fun formatThrowsPerWin(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

@Composable
fun RpsPerWinLabel(
    throwsPerWin: Double,
    modifier: Modifier = Modifier,
    iconSize: Dp = MoveStatIconSize,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = rpsPerWinColor(throwsPerWin),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatThrowsPerWin(throwsPerWin),
            style = textStyle,
            color = color,
            maxLines = 1,
        )
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
        Text(
            text = "/Win",
            style = textStyle,
            color = color,
            maxLines = 1,
        )
    }
}
