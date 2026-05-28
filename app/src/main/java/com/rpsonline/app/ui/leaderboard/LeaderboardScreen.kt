package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.rpsonline.app.ui.theme.isRpsDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.R
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.ui.components.HomeOutlinedButton
import com.rpsonline.app.ui.components.PlayerSummaryContent
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.LeaderboardViewModel

private const val LeaderboardEntryContentType = 0

@Composable
fun LeaderboardScreen(
    onHome: () -> Unit,
    onPlayerProfile: (userId: String) -> Unit,
    viewModel: LeaderboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.layoutInfo.totalItemsCount,
        uiState.hasMore,
        uiState.isLoading,
        uiState.isAppending,
        uiState.entries.size,
    ) {
        if (!uiState.hasMore || uiState.isLoading || uiState.isAppending) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return@LaunchedEffect
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisibleIndex >= layoutInfo.totalItemsCount - 2) {
            viewModel.loadMore()
        }
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
    ) {
        Text(
            text = stringResource(R.string.leaderboard),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                RpsLoadingColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    state = listState,
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

                    item {
                        if (uiState.isAppending) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HomeOutlinedButton(onClick = onHome, label = stringResource(R.string.back_to_home))
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
    val nameLine = buildString {
        append("#$rank ${entry.displayName}")
        if (isCurrentUser) append(" · You")
    }
    val rankLabelColor = when {
        isCurrentUser -> Color.Unspecified
        else -> leaderboardRankLabelColor(rank, isRpsDarkTheme())
    }
    PlayerSummaryContent(
        nameLine = nameLine,
        nameColor = if (isCurrentUser) {
            MaterialTheme.colorScheme.primary
        } else if (rankLabelColor == Color.Unspecified) {
            MaterialTheme.colorScheme.onSurface
        } else {
            rankLabelColor
        },
        wins = entry.wins,
        losses = entry.losses,
        draws = entry.draws,
        roundsWon = entry.roundsWon,
        roundsLost = entry.roundsLost,
        roundsDraw = entry.roundsDraw,
        throwsRock = entry.throwsRock,
        throwsPaper = entry.throwsPaper,
        throwsScissors = entry.throwsScissors,
        elo = entry.elo,
    )
}
