package com.rpsonline.app.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WinRoundBanner(
    myChoice: String?,
    opponentChoice: String?,
    awaitingNextRound: Boolean,
    modifier: Modifier = Modifier,
) {
    RoundOutcomeCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Default.EmojiEvents,
        headline = "You won the round!",
        subtitle = if (awaitingNextRound) {
            "Point scored — pick your move for the next round."
        } else {
            "Point scored."
        },
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        choiceSeparator = "vs",
        modifier = modifier,
    )
}
