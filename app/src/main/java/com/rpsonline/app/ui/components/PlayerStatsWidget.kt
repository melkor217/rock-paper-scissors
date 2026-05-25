package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.ui.leaderboard.PlayerThrowStatsColumn
import com.rpsonline.app.ui.leaderboard.hasThrowStats

@Composable
fun PlayerStatsWidget(
    displayName: String,
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    eloOverride: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val elo = eloOverride ?: profile?.elo ?: 1000
    val wins = profile?.wins ?: 0
    val losses = profile?.losses ?: 0
    val throwsRock = profile?.throwsRock ?: 0
    val throwsPaper = profile?.throwsPaper ?: 0
    val throwsScissors = profile?.throwsScissors ?: 0
    val showThrowStats = hasThrowStats(wins, throwsRock, throwsPaper, throwsScissors)
    val statStyle = MaterialTheme.typography.bodySmall
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
        ) {
            PlayerStatsWidgetBody(
                displayName = displayName,
                elo = elo,
                wins = wins,
                losses = losses,
                showThrowStats = showThrowStats,
                throwsRock = throwsRock,
                throwsPaper = throwsPaper,
                throwsScissors = throwsScissors,
                statStyle = statStyle,
                showChevron = true,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
        ) {
            PlayerStatsWidgetBody(
                displayName = displayName,
                elo = elo,
                wins = wins,
                losses = losses,
                showThrowStats = showThrowStats,
                throwsRock = throwsRock,
                throwsPaper = throwsPaper,
                throwsScissors = throwsScissors,
                statStyle = statStyle,
                showChevron = false,
            )
        }
    }
}

@Composable
private fun PlayerStatsWidgetBody(
    displayName: String,
    elo: Int,
    wins: Int,
    losses: Int,
    showThrowStats: Boolean,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
    statStyle: TextStyle,
    showChevron: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayName,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EloRatingText(
                        elo = elo,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "ELO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WinLossStatLine(
                    wins = wins,
                    losses = losses,
                    textStyle = statStyle,
                )
            }
            if (showThrowStats) {
                PlayerThrowStatsColumn(
                    wins = wins,
                    throwsRock = throwsRock,
                    throwsPaper = throwsPaper,
                    throwsScissors = throwsScissors,
                    modifier = Modifier.weight(1f),
                    textStyle = statStyle,
                )
            }
        }
    }
}
