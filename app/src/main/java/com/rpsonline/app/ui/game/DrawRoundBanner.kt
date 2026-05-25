package com.rpsonline.app.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DrawRoundBanner(
    myChoice: String?,
    opponentChoice: String?,
    isReplay: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    RoundOutcomeCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = Icons.Default.Balance,
        headline = "Draw!",
        subtitle = when {
            compact && isReplay -> "Replay this round."
            compact -> "No point awarded."
            isReplay -> "Replay this round. Score unchanged."
            else -> "No point awarded."
        },
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        choiceSeparator = "=",
        modifier = modifier,
        compact = compact,
        opponentLabel = opponentLabel,
    )
}
