package com.rpsonline.app.ui.leaderboard

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
    if (percent == null) return MaterialTheme.colorScheme.onSurfaceVariant
    return leaderboardSpectrumColor(percent.toFloat())
}
