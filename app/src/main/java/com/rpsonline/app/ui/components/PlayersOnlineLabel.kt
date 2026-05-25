package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

fun playersOnlineLabel(count: Int): String = when (count) {
    1 -> "1 player online"
    else -> "$count players online"
}

@Composable
fun PlayersOnlineLabel(
    count: Int,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Text(
        text = playersOnlineLabel(count),
        modifier = modifier,
        style = if (emphasized) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.bodyMedium
        },
        fontWeight = if (emphasized) FontWeight.SemiBold else null,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = if (emphasized) TextAlign.Center else TextAlign.Start,
    )
}
