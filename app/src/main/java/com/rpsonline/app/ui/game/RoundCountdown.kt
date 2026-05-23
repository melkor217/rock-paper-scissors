package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.domain.GameRules

@Composable
fun RoundCountdown(
    secondsRemaining: Int?,
    isResolvingTimeout: Boolean = false,
    hasSubmittedMove: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (secondsRemaining == null) return

    val timerSize = if (compact) 56.dp else 72.dp

    if (isResolvingTimeout) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(if (compact) 40.dp else 48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasSubmittedMove) {
                    "Opponent timed out — resolving…"
                } else {
                    "Time's up — resolving round…"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    val atZero = secondsRemaining <= 0
    val urgent = !hasSubmittedMove && !atZero && secondsRemaining <= 5
    val color = if (urgent) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val progress = secondsRemaining.toFloat() / GameRules.ROUND_TIMEOUT_SECONDS

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .size(timerSize)
                    .graphicsLayer { scaleX = -1f },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth,
            )
            Text(
                text = secondsRemaining.toString(),
                style = if (compact) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                atZero -> "Time's up — resolving round…"
                hasSubmittedMove -> "Waiting for opponent's pick"
                urgent -> "Hurry — timeout forfeits the match!"
                else -> "Seconds to pick"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (urgent) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
