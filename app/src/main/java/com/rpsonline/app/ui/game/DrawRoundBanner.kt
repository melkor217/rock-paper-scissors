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
) {
    RoundOutcomeCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = Icons.Default.Balance,
        headline = "Draw!",
        subtitle = if (isReplay) {
            "Same move — replay this round. Score unchanged."
        } else {
            "Same move — no point awarded."
        },
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        choiceSeparator = "=",
        modifier = modifier,
    )
}
