package com.rpsonline.app.ui.leaderboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rpsonline.app.data.model.LeaderboardEntry

fun winRatePercent(wins: Int, losses: Int, draws: Int = 0): Int? {
    val decidedGames = wins + losses
    return decidedGames.takeIf { it > 0 }?.let { (wins * 100) / it }
}

fun LeaderboardEntry.winRatePercent(): Int? = winRatePercent(wins, losses, draws)

@Composable
fun leaderboardWinRateColor(percent: Int?): Color {
    if (percent == null) return MaterialTheme.colorScheme.onSurfaceVariant
    return leaderboardSpectrumColor(percent.toFloat())
}
