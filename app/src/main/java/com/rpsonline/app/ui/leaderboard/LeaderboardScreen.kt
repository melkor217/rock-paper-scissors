package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.LeaderboardViewModel

@Composable
fun LeaderboardScreen(
    onBackToHome: () -> Unit,
    onPlayerProfile: (userId: String) -> Unit,
    viewModel: LeaderboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
    ) {
        Text(
            text = "Leaderboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(uiState.entries) { index, entry ->
                        val rank = index + 1
                        val isCurrentUser = entry.uid == uiState.currentUserId
                        LeaderboardEntryCard(
                            rank = rank,
                            isCurrentUser = isCurrentUser,
                            onClick = { onPlayerProfile(entry.uid) },
                        ) {
                            LeaderboardEntryContent(
                                rank = rank,
                                entry = entry,
                                isCurrentUser = isCurrentUser,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back to Home")
        }
    }
}

@Composable
private fun LeaderboardEntryContent(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = buildString {
                    append("#$rank ${entry.displayName}")
                    if (isCurrentUser) append(" · You")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    leaderboardRankLabelColor(rank)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "W ${entry.wins} / L ${entry.losses}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                entry.winRatePercent()?.let { winRate ->
                    Text(
                        text = " · $winRate%",
                        style = MaterialTheme.typography.bodySmall,
                        color = leaderboardWinRateColor(winRate),
                        maxLines = 1,
                    )
                }
            }
            entry.throwsPerWin()?.let { throwsPerWin ->
                RpsPerWinLabel(throwsPerWin = throwsPerWin)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThrowDistributionRadialChart(
                rock = entry.throwsRock,
                paper = entry.throwsPaper,
                scissors = entry.throwsScissors,
                size = 56.dp,
            )
            Text(
                text = "${entry.elo}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 40.dp),
            )
        }
    }
}
