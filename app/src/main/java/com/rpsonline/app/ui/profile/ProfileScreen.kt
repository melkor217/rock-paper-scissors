package com.rpsonline.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.ui.components.HomeOutlinedButton
import com.rpsonline.app.ui.components.MatchHistoryLoadingSection
import com.rpsonline.app.ui.components.MatchRecapCard
import com.rpsonline.app.ui.components.RpsCard
import com.rpsonline.app.ui.components.MatchHistoryCardHeader
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.ProfileSummaryStatsCard
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    userId: String,
    onHome: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(userId) {
        viewModel.load(userId)
        onPauseOrDispose { }
    }

    Column(modifier = Modifier.rpsScreenPadding()) {
        Text(
            text = uiState.profile?.displayName ?: "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading && uiState.profile == null -> {
                RpsLoadingColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    message = "Loading profile…",
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val profile = uiState.profile
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        ProfileStatsCard(
                            elo = profile?.elo ?: 1000,
                            wins = profile?.wins ?: 0,
                            losses = profile?.losses ?: 0,
                            draws = profile?.draws ?: 0,
                            roundsWon = profile?.roundsWon ?: 0,
                            roundsLost = profile?.roundsLost ?: 0,
                            roundsDraw = profile?.roundsDraw ?: 0,
                            throwsRock = profile?.throwsRock ?: 0,
                            throwsPaper = profile?.throwsPaper ?: 0,
                            throwsScissors = profile?.throwsScissors ?: 0,
                        )
                    }
                    item {
                        Text(
                            text = if (uiState.isOwnProfile) {
                                "Last 10 matches"
                            } else {
                                "Matches you played together"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (uiState.isMatchHistoryLoading) {
                        item {
                            MatchHistoryLoadingSection()
                        }
                    } else if (uiState.matchHistory.isEmpty()) {
                        item {
                            Text(
                                text = if (uiState.isOwnProfile) {
                                    "No completed matches yet."
                                } else {
                                    "No shared matches yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = uiState.matchHistory,
                            key = { it.matchId },
                        ) { entry ->
                            MatchHistoryCard(entry = entry)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HomeOutlinedButton(onClick = onHome)
    }
}

@Composable
private fun ProfileStatsCard(
    elo: Int,
    wins: Int,
    losses: Int,
    draws: Int,
    roundsWon: Int,
    roundsLost: Int,
    roundsDraw: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
) {
    ProfileSummaryStatsCard(
        elo = elo,
        wins = wins,
        losses = losses,
        draws = draws,
        roundsWon = roundsWon,
        roundsLost = roundsLost,
        roundsDraw = roundsDraw,
        throwsRock = throwsRock,
        throwsPaper = throwsPaper,
        throwsScissors = throwsScissors,
    )
}

@Composable
private fun MatchHistoryCard(
    entry: MatchHistoryEntry,
) {
    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MatchHistoryCardHeader(
                entry = entry,
                lastActivityAt = entry.lastActivityAt,
                outcomeLabel = matchOutcomeLabel(entry),
                outcomeColor = matchOutcomeColor(entry),
            )
            if (entry.recaps.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MatchRecapCard(
                    recaps = entry.recaps,
                    title = null,
                    embedded = true,
                )
            }
        }
    }
}

private fun matchOutcomeLabel(entry: MatchHistoryEntry): String = when {
    entry.isAbandoned -> "Cancelled"
    entry.isDraw -> "Draw"
    entry.won == true -> "Win"
    entry.won == false -> "Loss"
    else -> "—"
}

@Composable
private fun matchOutcomeColor(entry: MatchHistoryEntry): androidx.compose.ui.graphics.Color = when {
    entry.isAbandoned -> MaterialTheme.colorScheme.onSurfaceVariant
    entry.isDraw -> MaterialTheme.colorScheme.tertiary
    entry.won == true -> MaterialTheme.colorScheme.primary
    entry.won == false -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
