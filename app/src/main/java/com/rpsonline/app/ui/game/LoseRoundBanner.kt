package com.rpsonline.app.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoseRoundBanner(
    myChoice: String?,
    opponentChoice: String?,
    roundNumber: Int,
    awaitingNextRound: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    RoundOutcomeCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Default.HeartBroken,
        headline = "You lost Round #$roundNumber",
        subtitle = when {
            compact && awaitingNextRound -> ""
            compact -> "Opponent scored."
            awaitingNextRound -> "Opponent scored — pick your move for the next round."
            else -> "Opponent scored a point."
        },
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        choiceSeparator = "vs",
        modifier = modifier,
        compact = compact,
        opponentLabel = opponentLabel,
    )
}
