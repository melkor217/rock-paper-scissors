package com.rpsonline.app.ui.leaderboard

import androidx.compose.ui.graphics.Color

fun leaderboardPodiumStripeColor(rank: Int, darkTheme: Boolean): Color? = when (rank) {
    1 -> if (darkTheme) Color(0xFFFFD54F) else Color(0xFFD4A017)
    2 -> if (darkTheme) Color(0xFFDCE4F0) else Color(0xFF8E96A3)
    3 -> if (darkTheme) Color(0xFFE89B5C) else Color(0xFFB87333)
    else -> null
}

fun leaderboardPodiumRankLabelColor(rank: Int, darkTheme: Boolean): Color? = when (rank) {
    1 -> if (darkTheme) Color(0xFFFFF0A0) else Color(0xFF6A5800)
    2 -> if (darkTheme) Color(0xFFE8ECF4) else Color(0xFF4A5058)
    3 -> if (darkTheme) Color(0xFFFFD4B0) else Color(0xFF6A4018)
    else -> null
}
