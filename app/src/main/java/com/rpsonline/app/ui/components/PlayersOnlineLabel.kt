package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

fun playersOnlineLabel(count: Int): String = when (count) {
    1 -> "1 player online"
    else -> "$count players online"
}

@Composable
fun PlayersOnlineLabel(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = playersOnlineLabel(count),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
