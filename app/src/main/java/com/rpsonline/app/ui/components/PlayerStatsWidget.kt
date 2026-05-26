package com.rpsonline.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rpsonline.app.data.model.UserProfile

@Composable
fun PlayerStatsWidget(
    displayName: String,
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    eloOverride: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    ProfileSummaryStatsCard(
        elo = eloOverride ?: profile?.elo ?: 1000,
        wins = profile?.wins ?: 0,
        losses = profile?.losses ?: 0,
        draws = profile?.draws ?: 0,
        roundsWon = profile?.roundsWon ?: 0,
        roundsLost = profile?.roundsLost ?: 0,
        roundsDraw = profile?.roundsDraw ?: 0,
        throwsRock = profile?.throwsRock ?: 0,
        throwsPaper = profile?.throwsPaper ?: 0,
        throwsScissors = profile?.throwsScissors ?: 0,
        modifier = modifier,
        showHeader = true,
        headerTitle = displayName,
        showChevron = onClick != null,
        onClick = onClick,
    )
}
