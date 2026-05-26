package com.rpsonline.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.ui.components.PlayersOnlineLabel
import com.rpsonline.app.ui.components.formatMatchMode
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.util.playMatchFoundSound
import com.rpsonline.app.viewmodel.MatchmakingStatus
import com.rpsonline.app.viewmodel.MatchmakingViewModel

@Composable
fun MatchmakingScreen(
    matchMode: MatchMode,
    onMatchFound: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: MatchmakingViewModel = viewModel(factory = MatchmakingViewModel.factory(matchMode)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startMatchmaking()
    }

    LaunchedEffect(uiState.status, uiState.matchId) {
        if (uiState.status == MatchmakingStatus.MATCHED && uiState.matchId != null) {
            playMatchFoundSound(context.applicationContext)
            onMatchFound(uiState.matchId!!)
        }
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        uiState.onlinePlayerCount?.let { count ->
            PlayersOnlineLabel(count = count)
            Spacer(modifier = Modifier.height(16.dp))
        }
        when (uiState.status) {
            MatchmakingStatus.SEARCHING -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Searching for ${formatMatchMode(matchMode)} opponent…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Matching by similar ELO and format",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatQueueElapsed(uiState.queueElapsedSeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = {
                    viewModel.cancelMatchmaking()
                    onCancel()
                }) {
                    Text("Cancel")
                }
            }

            MatchmakingStatus.ERROR -> {
                Text(
                    text = uiState.error ?: "Matchmaking failed",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.startMatchmaking() }) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Back")
                }
            }

            MatchmakingStatus.MATCHED -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Match found! Starting game…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            MatchmakingStatus.IDLE -> {
                Text(
                    text = "Preparing matchmaking…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

private fun formatQueueElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "In queue: %d:%02d".format(minutes, secs)
}
