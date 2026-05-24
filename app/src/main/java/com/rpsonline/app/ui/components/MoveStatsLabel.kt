package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rpsonline.app.data.model.UserProfile

fun moveStatsLabel(profile: UserProfile): String =
    "Rock: ${profile.rockCount}  •  Paper: ${profile.paperCount}  •  Scissors: ${profile.scissorsCount}"

@Composable
fun MoveStatsLabel(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    Text(
        text = moveStatsLabel(profile),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
