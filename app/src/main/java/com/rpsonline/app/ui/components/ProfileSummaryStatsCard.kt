package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpsonline.app.ui.leaderboard.RpsPerWinLabel
import com.rpsonline.app.ui.leaderboard.hasThrowStats
import com.rpsonline.app.ui.leaderboard.throwsPerWin

private val ProfileCardPadding = 10.dp
private val ProfileHeaderStatsGap = 6.dp
private val ProfileStatsRowGap = 4.dp

@Composable
fun ProfileSummaryStatsCard(
    elo: Int,
    wins: Int,
    losses: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
    headerTitle: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val statStyle = MaterialTheme.typography.labelMedium
    val showThrowStats = hasThrowStats(wins, throwsRock, throwsPaper, throwsScissors)

    val cardModifier = modifier.fillMaxWidth()
    val body: @Composable () -> Unit = {
        ProfileSummaryStatsBody(
            elo = elo,
            wins = wins,
            losses = losses,
            throwsRock = throwsRock,
            throwsPaper = throwsPaper,
            throwsScissors = throwsScissors,
            showHeader = showHeader,
            headerTitle = headerTitle,
            showChevron = showChevron,
            showThrowStats = showThrowStats,
            statStyle = statStyle,
        )
    }

    if (onClick != null) {
        RpsCard(onClick = onClick, modifier = cardModifier, content = { body() })
    } else {
        RpsCard(modifier = cardModifier, content = { body() })
    }
}

@Composable
private fun ProfileSummaryStatsBody(
    elo: Int,
    wins: Int,
    losses: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
    showHeader: Boolean,
    headerTitle: String?,
    showChevron: Boolean,
    showThrowStats: Boolean,
    statStyle: androidx.compose.ui.text.TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ProfileCardPadding),
        verticalArrangement = Arrangement.spacedBy(ProfileHeaderStatsGap),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle ?: "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showChevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ProfileStatsRowGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    EloRatingText(
                        elo = elo,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "ELO",
                        style = statStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                WinLossStatLine(
                    wins = wins,
                    losses = losses,
                    textStyle = statStyle,
                )
            }

            if (showThrowStats) {
                val throwsPerWinValue = throwsPerWin(wins, throwsRock, throwsPaper, throwsScissors)
                if (throwsPerWinValue != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RpsPerWinLabel(
                            throwsPerWin = throwsPerWinValue,
                            textStyle = statStyle,
                            showMoveIcons = true,
                        )
                        ThrowCountRow(
                            rock = throwsRock,
                            paper = throwsPaper,
                            scissors = throwsScissors,
                            textStyle = statStyle,
                            iconSize = MoveStatIconSize,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        )
                    }
                }
            }
        }
    }
}
