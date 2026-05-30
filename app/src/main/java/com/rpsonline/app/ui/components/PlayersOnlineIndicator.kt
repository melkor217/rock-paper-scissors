package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rpsonline.app.R

@Composable
fun PlayersOnlineIndicator(
    count: Int?,
    modifier: Modifier = Modifier,
) {
    val description = when (count) {
        null -> stringResource(R.string.players_online_loading)
        else -> stringResource(R.string.players_online_count, count)
    }
    FourDigitSegmentedDisplay(
        value = count,
        modifier = modifier
            .height(SegmentedDisplayHeight)
            .semantics {
                contentDescription = description
            },
    )
}
