package com.rpsonline.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.ui.components.PlayersOnlineLabel
import com.rpsonline.app.ui.components.RpsCard
import com.rpsonline.app.ui.components.formatMatchModes
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.matchmaking.formatQueueTime
import com.rpsonline.app.viewmodel.MatchmakingStatus
import com.rpsonline.app.viewmodel.MatchmakingViewModel

@Composable
fun MatchmakingScreen(
    matchModes: Set<MatchMode>,
    onMatchFound: (String) -> Unit,
    viewModel: MatchmakingViewModel = viewModel(factory = MatchmakingViewModel.factory(matchModes)),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startMatchmaking()
    }

    LaunchedEffect(uiState.status, uiState.matchId) {
        if (uiState.status == MatchmakingStatus.MATCHED && uiState.matchId != null) {
            onMatchFound(uiState.matchId!!)
        }
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        uiState.onlinePlayerCount?.let { count ->
            PlayersOnlineLabel(
                count = count,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        when (uiState.status) {
            MatchmakingStatus.SEARCHING -> {
                MatchmakingSearchCard(
                    matchModes = matchModes,
                    queueElapsedSeconds = uiState.queueElapsedSeconds,
                )
            }

            MatchmakingStatus.ERROR -> {
                MatchmakingMessageCard(
                    title = "Couldn’t join queue",
                    body = uiState.error ?: "Matchmaking failed",
                    isError = true,
                )
            }

            MatchmakingStatus.MATCHED -> {
                MatchmakingMessageCard(
                    title = "Match found",
                    body = "Starting game…",
                )
            }

            MatchmakingStatus.IDLE -> {
                MatchmakingMessageCard(
                    title = "Preparing",
                    body = "Setting up matchmaking…",
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        when (uiState.status) {
            MatchmakingStatus.SEARCHING -> Unit

            MatchmakingStatus.ERROR -> {
                Button(
                    onClick = { viewModel.startMatchmaking() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry")
                }
            }

            MatchmakingStatus.IDLE,
            MatchmakingStatus.MATCHED,
            -> Unit
        }
    }
}

@Composable
private fun MatchmakingSearchCard(
    matchModes: Set<MatchMode>,
    queueElapsedSeconds: Long,
) {
    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Finding an opponent",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = formatMatchModes(matchModes),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Matched by similar rating",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "In queue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatQueueTime(queueElapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MatchmakingMessageCard(
    title: String,
    body: String,
    isError: Boolean = false,
) {
    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isError) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
            }
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
