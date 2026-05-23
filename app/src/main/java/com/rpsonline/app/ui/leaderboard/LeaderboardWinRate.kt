package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rpsonline.app.data.model.LeaderboardEntry

fun LeaderboardEntry.winRatePercent(): Int? {
    val games = wins + losses
    return games.takeIf { it > 0 }?.let { (wins * 100) / it }
}

@Composable
fun leaderboardWinRateColor(percent: Int?): Color {
    val scheme = MaterialTheme.colorScheme
    if (percent == null) return scheme.onSurfaceVariant

    val amber = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)
    return when {
        percent >= 60 -> scheme.primary
        percent >= 50 -> scheme.tertiary
        percent >= 40 -> amber
        else -> scheme.error
    }
}
