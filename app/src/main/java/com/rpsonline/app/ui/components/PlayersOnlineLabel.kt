package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rpsonline.app.R

@Composable
fun PlayersOnlineLabel(
    count: Int,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Text(
        text = stringResource(R.string.players_online_count, count),
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
        textAlign = TextAlign.Center,
    )
}
