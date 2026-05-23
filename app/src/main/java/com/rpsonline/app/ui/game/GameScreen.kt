package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundResult
import com.rpsonline.app.domain.GameRules
import com.rpsonline.app.ui.components.rpsScreenPadding
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
        modifier = Modifier.rpsScreenPadding(),
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

        val showCountdown = match.status == MatchStatus.ACTIVE &&
            match.openRound()?.deadline != null &&
            uiState.countdownSeconds != null
        if (showCountdown) {
            RoundCountdown(
                secondsRemaining = uiState.countdownSeconds,
                isResolvingTimeout = uiState.isResolvingTimeout,
                hasSubmittedMove = uiState.hasSubmittedMove,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

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
        val drawReplay = match.pendingDrawReplay()
        val pendingOutcome = match.pendingRoundOutcome()
        val openRound = match.openRound()
        val myLockedChoice = myLockedChoice(userId, match, openRound, uiState.lockedMove)
        val showDrawReveal = currentRound?.winner == "tie" &&
            currentRound.player1Choice != null &&
            currentRound.player2Choice != null
        val showOutcomeReveal = currentRound?.winner != null &&
            currentRound.winner != "tie" &&
            currentRound.player1Choice != null &&
            currentRound.player2Choice != null

        when {
            showDrawReveal || drawReplay != null -> {
                val round = if (showDrawReveal) currentRound!! else drawReplay!!
                val (myChoice, oppChoice) = round.choicesFor(userId, match)
                DrawRoundBanner(
                    myChoice = myChoice,
                    opponentChoice = oppChoice,
                    isReplay = drawReplay != null,
                )
                when {
                    drawReplay != null && uiState.hasSubmittedMove -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        myLockedChoice?.let { WaitingForOpponentCard(myChoice = it) }
                    }

                    drawReplay != null -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pick your move to replay the round (${GameRules.ROUND_TIMEOUT_SECONDS}s limit)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MovePicker(
                            isSubmitting = uiState.isSubmitting,
                            onMove = viewModel::submitMove,
                        )
                    }
                }
            }

            showOutcomeReveal || pendingOutcome != null -> {
                val round = if (showOutcomeReveal) currentRound!! else pendingOutcome!!
                val (myChoice, oppChoice) = round.choicesFor(userId, match)
                val wonRound = round.winner == userId
                val awaitingNextRound = pendingOutcome != null && openRound != null

                if (wonRound) {
                    WinRoundBanner(
                        myChoice = myChoice,
                        opponentChoice = oppChoice,
                        awaitingNextRound = awaitingNextRound,
                    )
                } else {
                    LoseRoundBanner(
                        myChoice = myChoice,
                        opponentChoice = oppChoice,
                        awaitingNextRound = awaitingNextRound,
                    )
                }

                when {
                    awaitingNextRound && uiState.hasSubmittedMove -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        myLockedChoice?.let { WaitingForOpponentCard(myChoice = it) }
                    }

                    awaitingNextRound -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pick your move for round ${openRound!!.roundNumber} (${GameRules.ROUND_TIMEOUT_SECONDS}s limit)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MovePicker(
                            isSubmitting = uiState.isSubmitting,
                            onMove = viewModel::submitMove,
                        )
                    }
                }
            }

            uiState.hasSubmittedMove -> {
                myLockedChoice?.let { WaitingForOpponentCard(myChoice = it) }
            }

            uiState.isSubmitting -> {
                CircularProgressIndicator()
            }

            else -> {
                Text(
                    text = "Pick your move — ${GameRules.ROUND_TIMEOUT_SECONDS}s per round",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                MovePicker(
                    isSubmitting = uiState.isSubmitting,
                    onMove = viewModel::submitMove,
                )
            }
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun myLockedChoice(
    userId: String,
    match: Match,
    openRound: RoundResult?,
    lockedMove: Move?,
): String? {
    val fromServer = openRound?.let { round ->
        if (userId == match.player1) round.player1Choice else round.player2Choice
    }
    return fromServer ?: lockedMove?.name
}

private fun RoundResult.choicesFor(userId: String, match: Match): Pair<String?, String?> {
    val myChoice = if (userId == match.player1) player1Choice else player2Choice
    val oppChoice = if (userId == match.player1) player2Choice else player1Choice
    return myChoice to oppChoice
}

@Composable
private fun MovePicker(
    isSubmitting: Boolean,
    onMove: (Move) -> Unit,
) {
    if (isSubmitting) {
        CircularProgressIndicator()
        return
    }
    MoveButton("Rock", Icons.Default.Landscape) { onMove(Move.ROCK) }
    Spacer(modifier = Modifier.height(8.dp))
    MoveButton("Paper", Icons.Default.Description) { onMove(Move.PAPER) }
    Spacer(modifier = Modifier.height(8.dp))
    MoveButton("Scissors", Icons.Default.ContentCut) { onMove(Move.SCISSORS) }
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
