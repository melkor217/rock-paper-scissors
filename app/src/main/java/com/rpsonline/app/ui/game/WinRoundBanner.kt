package com.rpsonline.app.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WinRoundBanner(
    myChoice: String?,
    opponentChoice: String?,
    roundNumber: Int,
    awaitingNextRound: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    RoundOutcomeBanner(
        kind = RoundBannerKind.Win,
        roundNumber = roundNumber,
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        showFollowUpHint = awaitingNextRound,
        modifier = modifier,
        compact = compact,
        opponentLabel = opponentLabel,
    )
}
