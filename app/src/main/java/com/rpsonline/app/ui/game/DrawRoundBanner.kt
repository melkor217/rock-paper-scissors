package com.rpsonline.app.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DrawRoundBanner(
    myChoice: String?,
    opponentChoice: String?,
    roundNumber: Int,
    isReplay: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    RoundOutcomeBanner(
        kind = RoundBannerKind.Draw,
        roundNumber = roundNumber,
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        showFollowUpHint = isReplay,
        modifier = modifier,
        compact = compact,
        opponentLabel = opponentLabel,
    )
}
