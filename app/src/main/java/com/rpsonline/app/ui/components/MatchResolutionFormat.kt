package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.ViewerMatchResolution

/** Short label for match history headers (Win, Loss, Draw, Cancelled). */
fun viewerMatchResolutionLabel(resolution: ViewerMatchResolution): String = when (resolution) {
    ViewerMatchResolution.ABANDONED -> "Cancelled"
    ViewerMatchResolution.DRAW -> "Draw"
    ViewerMatchResolution.WIN -> "Win"
    ViewerMatchResolution.LOSS -> "Loss"
}

/** Large headline on the match result screen (Victory!, Defeat, Draw, Cancelled). */
fun viewerMatchResolutionHeadline(resolution: ViewerMatchResolution?): String = when (resolution) {
    ViewerMatchResolution.WIN -> "Victory!"
    ViewerMatchResolution.LOSS -> "Defeat"
    ViewerMatchResolution.DRAW -> "Draw"
    ViewerMatchResolution.ABANDONED -> "Cancelled"
    null -> "—"
}

fun viewerMatchResolutionSubtitle(resolution: ViewerMatchResolution?): String? = null

@Composable
fun viewerMatchResolutionColor(resolution: ViewerMatchResolution): Color = when (resolution) {
    ViewerMatchResolution.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
    ViewerMatchResolution.DRAW -> MaterialTheme.colorScheme.tertiary
    ViewerMatchResolution.WIN -> MaterialTheme.colorScheme.primary
    ViewerMatchResolution.LOSS -> MaterialTheme.colorScheme.error
}

@Composable
fun viewerMatchResolutionHeadlineColor(resolution: ViewerMatchResolution?): Color = when (resolution) {
    ViewerMatchResolution.WIN -> MaterialTheme.colorScheme.primary
    ViewerMatchResolution.LOSS -> MaterialTheme.colorScheme.error
    ViewerMatchResolution.DRAW -> MaterialTheme.colorScheme.tertiary
    ViewerMatchResolution.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MatchResolutionOutcomeHeader(
    resolution: ViewerMatchResolution?,
    outcomeDetail: String? = null,
    modifier: Modifier = Modifier,
) {
    if (resolution == null) {
        Text(
            text = viewerMatchResolutionHeadline(resolution),
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall,
            color = viewerMatchResolutionHeadlineColor(resolution),
            textAlign = TextAlign.Center,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = viewerMatchResolutionHeadline(resolution),
            style = MaterialTheme.typography.displaySmall,
            color = viewerMatchResolutionHeadlineColor(resolution),
        )
        viewerMatchResolutionSubtitle(resolution)?.let { subtitle ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = viewerMatchResolutionHeadlineColor(resolution),
            )
        }
        outcomeDetail?.let { detail ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
