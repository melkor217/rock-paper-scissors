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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun formatClockSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun MatchClockDisplay(
    secondsRemaining: Int,
    label: String,
    isLow: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isLow) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatClockSeconds(secondsRemaining),
            style = if (compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineSmall
            },
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
fun GameTimerRow(
    myClockSeconds: Int,
    opponentClockSeconds: Int,
    roundSecondsRemaining: Int?,
    isResolvingTimeout: Boolean,
    hasSubmittedMove: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MatchClockDisplay(
            secondsRemaining = myClockSeconds,
            label = "You",
            isLow = !hasSubmittedMove && myClockSeconds <= 10,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        if (roundSecondsRemaining != null) {
            RoundCountdown(
                secondsRemaining = roundSecondsRemaining,
                isResolvingTimeout = isResolvingTimeout,
                hasSubmittedMove = hasSubmittedMove,
                compact = compact,
                modifier = Modifier.weight(1.1f),
            )
        }
        MatchClockDisplay(
            secondsRemaining = opponentClockSeconds,
            label = "Opp",
            isLow = opponentClockSeconds <= 10,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}
