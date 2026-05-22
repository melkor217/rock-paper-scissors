package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.viewmodel.GameViewModel

@Composable
fun GameScreen(
    matchId: String,
    onMatchComplete: (String) -> Unit,
    viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(matchId)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val match = uiState.match
    val userId = uiState.userId

    LaunchedEffect(match?.status, match?.id) {
        if (match?.status == MatchStatus.COMPLETED) {
            onMatchComplete(matchId)
        }
    }

    if (match?.status == MatchStatus.COMPLETED) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (match == null || userId == null) {
            CircularProgressIndicator()
            return
        }

        Text(
            text = "vs ${match.opponentName(userId)}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Round ${match.currentRound}  •  Best of 3",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreColumn(label = "You", score = match.myWins(userId))
                Text(":", style = MaterialTheme.typography.headlineMedium)
                ScoreColumn(label = "Opponent", score = match.opponentWins(userId))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val currentRound = match.currentRoundData()
        when {
            currentRound?.winner == "tie" -> {
                Text("Tie! Replay this round.", style = MaterialTheme.typography.bodyLarge)
            }

            currentRound?.winner != null && currentRound.player1Choice != null -> {
                val myChoice = if (userId == match.player1) {
                    currentRound.player1Choice
                } else {
                    currentRound.player2Choice
                }
                val oppChoice = if (userId == match.player1) {
                    currentRound.player2Choice
                } else {
                    currentRound.player1Choice
                }
                Text("You: ${myChoice?.lowercase()}  •  Opponent: ${oppChoice?.lowercase()}")
                val roundWinner = currentRound.winner
                val message = when (roundWinner) {
                    "tie" -> "Round tied"
                    userId -> "You won the round!"
                    else -> "Opponent won the round"
                }
                Text(message, style = MaterialTheme.typography.titleMedium)
            }

            uiState.hasSubmittedMove -> {
                Text("Choice locked in. Waiting for opponent…")
                CircularProgressIndicator()
            }

            uiState.isSubmitting -> {
                CircularProgressIndicator()
            }

            else -> {
                Text("Pick your move (10s)", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                MoveButton("Rock", Icons.Default.Landscape) {
                    viewModel.submitMove(Move.ROCK)
                }
                Spacer(modifier = Modifier.height(8.dp))
                MoveButton("Paper", Icons.Default.Description) {
                    viewModel.submitMove(Move.PAPER)
                }
                Spacer(modifier = Modifier.height(8.dp))
                MoveButton("Scissors", Icons.Default.ContentCut) {
                    viewModel.submitMove(Move.SCISSORS)
                }
            }
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ScoreColumn(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("$score", style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun MoveButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = label)
        Text("  $label")
    }
}
