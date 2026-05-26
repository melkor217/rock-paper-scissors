package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.ui.leaderboard.eloRatingColor
import com.rpsonline.app.ui.theme.currentAppThemeStyle

@Composable
fun EloRatingText(
    elo: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val color = if (currentAppThemeStyle() == AppThemeStyle.CYBERPUNK) {
        MaterialTheme.colorScheme.tertiary
    } else {
        eloRatingColor(elo)
    }
    Text(
        text = "$elo",
        modifier = modifier,
        style = style,
        color = color,
    )
}
