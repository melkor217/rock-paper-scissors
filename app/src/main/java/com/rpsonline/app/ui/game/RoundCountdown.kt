package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpsonline.app.R
import com.rpsonline.app.domain.GameRules

@Composable
fun RoundCountdown(
    secondsRemaining: Int?,
    label: String = "",
    isRunning: Boolean = true,
    isResolvingTimeout: Boolean = false,
    hasSubmittedMove: Boolean = false,
    showFooter: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (secondsRemaining == null) return

    if (isResolvingTimeout) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CircularProgressIndicator(modifier = Modifier.size(if (compact) 40.dp else 48.dp))
            if (showFooter) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasSubmittedMove) {
                        stringResource(R.string.opponent_timed_out_resolving)
                    } else {
                        stringResource(R.string.times_up_resolving_round)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        return
    }

    val atZero = secondsRemaining <= 0
    val warning = !hasSubmittedMove && !atZero && secondsRemaining <= 30
    val urgent = !hasSubmittedMove && !atZero && secondsRemaining <= 10
    val labelColor = if (isRunning) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        CircularGameClock(
            secondsRemaining = secondsRemaining,
            isRunning = isRunning,
            compact = compact,
            ringFullSeconds = GameRules.ROUND_TIMEOUT_SECONDS,
        )
        if (showFooter) {
            Spacer(modifier = Modifier.height(4.dp))
            val footerColor = when {
                urgent || warning -> when {
                    secondsRemaining <= 10 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = when {
                    atZero -> stringResource(R.string.times_up_resolving_round)
                    hasSubmittedMove -> stringResource(R.string.waiting_opponent_pick)
                    urgent -> stringResource(R.string.critical_clock_warning)
                    warning -> stringResource(R.string.hurry_clock_warning)
                    else -> stringResource(R.string.seconds_to_pick)
                },
                style = if (compact) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = footerColor,
            )
        }
    }
}
