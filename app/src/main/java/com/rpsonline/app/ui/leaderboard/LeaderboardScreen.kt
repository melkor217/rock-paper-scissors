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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.LeaderboardViewModel

private const val LeaderboardEntryContentType = 0

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
                    itemsIndexed(
                        items = uiState.entries,
                        key = { _, entry -> entry.uid },
                        contentType = { _, _ -> LeaderboardEntryContentType },
                    ) { index, entry ->
                        LeaderboardListItem(
                            rank = index + 1,
                            entry = entry,
                            isCurrentUser = entry.uid == uiState.currentUserId,
                            onClick = { onPlayerProfile(entry.uid) },
                        )
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
private fun LeaderboardListItem(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
) {
    LeaderboardEntryCard(
        rank = rank,
        isCurrentUser = isCurrentUser,
        onClick = onClick,
    ) {
        LeaderboardEntryContent(
            rank = rank,
            entry = entry,
            isCurrentUser = isCurrentUser,
        )
    }
}

@Composable
private fun LeaderboardEntryContent(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
) {
    val darkTheme = isSystemInDarkTheme()
    val nameLine = remember(rank, entry.displayName, isCurrentUser) {
        buildString {
            append("#$rank ${entry.displayName}")
            if (isCurrentUser) append(" · You")
        }
    }
    val winRate = entry.winRatePercent()
    val winRateColor = remember(winRate, darkTheme) {
        winRate?.let { leaderboardSpectrumColor(it.toFloat(), darkTheme) }
    }
    val rankLabelColor = remember(rank, isCurrentUser, darkTheme) {
        when {
            isCurrentUser -> Color.Unspecified
            else -> leaderboardRankLabelColor(rank, darkTheme)
        }
    }
    val throwsPerWin = entry.throwsPerWin()
    val rpsPerWinColor = remember(throwsPerWin, darkTheme) {
        throwsPerWin?.let { rpsPerWinColor(it, darkTheme) }
    }

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
                text = nameLine,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentUser) {
                    MaterialTheme.colorScheme.primary
                } else if (rankLabelColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    rankLabelColor
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
                if (winRate != null && winRateColor != null) {
                    Text(
                        text = " · $winRate%",
                        style = MaterialTheme.typography.bodySmall,
                        color = winRateColor,
                        maxLines = 1,
                    )
                }
            }
            if (throwsPerWin != null && rpsPerWinColor != null) {
                RpsPerWinLabel(
                    throwsPerWin = throwsPerWin,
                    color = rpsPerWinColor,
                )
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
