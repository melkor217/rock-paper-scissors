package com.rpsonline.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.viewmodel.MatchmakingStatus
import com.rpsonline.app.viewmodel.MatchmakingViewModel

@Composable
fun MatchmakingScreen(
    onMatchFound: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: MatchmakingViewModel = viewModel(),
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.status) {
            MatchmakingStatus.SEARCHING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Searching for opponent…", style = MaterialTheme.typography.titleMedium)
                Text("Matching by similar ELO rating")
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
                CircularProgressIndicator()
                Text("Match found! Starting game…")
            }

            MatchmakingStatus.IDLE -> {
                Text("Preparing matchmaking…")
            }
        }
    }
}
