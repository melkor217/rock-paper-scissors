package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.ui.leaderboard.ThrowDistributionRadialChart

private val SummaryRowHorizontalPadding = 10.dp
private val SummaryRowVerticalPadding = 6.dp
private val SummaryStatsLinesGap = 1.dp

@Composable
fun ProfileSummaryCardWidget(
    displayName: String,
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    eloOverride: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        PlayerSummaryBody(
            nameLine = displayName,
            nameColor = MaterialTheme.colorScheme.primary,
            wins = profile?.wins ?: 0,
            losses = profile?.losses ?: 0,
            draws = profile?.draws ?: 0,
            roundsWon = profile?.roundsWon ?: 0,
            roundsLost = profile?.roundsLost ?: 0,
            roundsDraw = profile?.roundsDraw ?: 0,
            throwsRock = profile?.throwsRock ?: 0,
            throwsPaper = profile?.throwsPaper ?: 0,
            throwsScissors = profile?.throwsScissors ?: 0,
            elo = eloOverride ?: profile?.elo ?: 1000,
            showChevron = onClick != null,
        )
    }
    if (onClick != null) {
        RpsCard(modifier = modifier.fillMaxWidth(), onClick = onClick, content = { content() })
    } else {
        RpsCard(modifier = modifier.fillMaxWidth(), content = { content() })
    }
}

@Composable
fun PlayerSummaryBody(
    nameLine: String,
    nameColor: Color,
    wins: Int,
    losses: Int,
    draws: Int,
    roundsWon: Int,
    roundsLost: Int,
    roundsDraw: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
    elo: Int,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    nameTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    statTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SummaryRowHorizontalPadding,
                vertical = SummaryRowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = nameLine,
                    style = nameTextStyle,
                    color = nameColor,
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
            Spacer(modifier = Modifier.height(0.dp))
            WinLossStatLine(
                wins = wins,
                losses = losses,
                draws = draws,
                textStyle = statTextStyle,
            )
            Spacer(modifier = Modifier.height(SummaryStatsLinesGap))
            RoundWinRateLine(
                wins = roundsWon,
                losses = roundsLost,
                draws = roundsDraw,
                textStyle = statTextStyle,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThrowDistributionRadialChart(
                rock = throwsRock,
                paper = throwsPaper,
                scissors = throwsScissors,
                size = 56.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                EloRatingText(
                    elo = elo,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .offset(y = (-1).dp),
                )
                Text(
                    text = "ELO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(y = (-1).dp),
                )
            }
        }
    }
}
