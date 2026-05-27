package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    null -> "Defeat"
}

fun viewerMatchResolutionSubtitle(resolution: ViewerMatchResolution?): String? = when (resolution) {
    ViewerMatchResolution.ABANDONED -> "This match was cancelled"
    ViewerMatchResolution.DRAW -> "Match tied — no winner"
    else -> null
}

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
    ViewerMatchResolution.LOSS, null -> MaterialTheme.colorScheme.error
    ViewerMatchResolution.DRAW -> MaterialTheme.colorScheme.onTertiaryContainer
    ViewerMatchResolution.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MatchResolutionOutcomeHeader(
    resolution: ViewerMatchResolution?,
    outcomeDetail: String? = null,
    modifier: Modifier = Modifier,
) {
    when (resolution) {
        ViewerMatchResolution.ABANDONED, ViewerMatchResolution.DRAW -> {
            val isDraw = resolution == ViewerMatchResolution.DRAW
            RpsCard(
                modifier = modifier.fillMaxWidth(),
                containerColor = if (isDraw) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
                },
                borderColor = if (isDraw) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isDraw) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Balance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = viewerMatchResolutionHeadline(resolution),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = viewerMatchResolutionHeadlineColor(resolution),
                            )
                        }
                    } else {
                        Text(
                            text = viewerMatchResolutionHeadline(resolution),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = viewerMatchResolutionHeadlineColor(resolution),
                        )
                    }
                    viewerMatchResolutionSubtitle(resolution)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = viewerMatchResolutionHeadlineColor(resolution),
                        )
                    }
                }
            }
        }
        ViewerMatchResolution.WIN, ViewerMatchResolution.LOSS, null -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = viewerMatchResolutionHeadline(resolution),
                    style = MaterialTheme.typography.displaySmall,
                    color = viewerMatchResolutionHeadlineColor(resolution),
                )
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
    }
}
