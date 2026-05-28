package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpsonline.app.R

fun formatClockSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun clockLabel(base: String): String = base

@Composable
fun MatchClockDisplay(
    secondsRemaining: Int,
    label: String,
    isRunning: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (isRunning) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = clockLabel(label),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        CircularGameClock(
            secondsRemaining = secondsRemaining,
            isRunning = isRunning,
            compact = compact,
        )
    }
}

@Composable
fun GameTimerRow(
    myClockSeconds: Int,
    opponentClockSeconds: Int,
    myClockRunning: Boolean,
    opponentClockRunning: Boolean,
    roundSecondsRemaining: Int?,
    isResolvingTimeout: Boolean,
    hasSubmittedMove: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        MatchClockDisplay(
            secondsRemaining = myClockSeconds,
            label = stringResource(R.string.you),
            isRunning = myClockRunning,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        if (roundSecondsRemaining != null) {
            RoundCountdown(
                secondsRemaining = roundSecondsRemaining,
                label = clockLabel(stringResource(R.string.round_label)),
                isRunning = !isResolvingTimeout,
                isResolvingTimeout = isResolvingTimeout,
                hasSubmittedMove = hasSubmittedMove,
                showFooter = false,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
        MatchClockDisplay(
            secondsRemaining = opponentClockSeconds,
            label = stringResource(R.string.opponent_short),
            isRunning = opponentClockRunning,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}
