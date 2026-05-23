package com.rpsonline.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onMatchAbandoned: () -> Unit,
    viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(matchId)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val match = uiState.match
    val userId = uiState.userId

    LaunchedEffect(match?.status, match?.id) {
        when (match?.status) {
            MatchStatus.COMPLETED -> onMatchComplete(matchId)
            MatchStatus.ABANDONED -> onMatchAbandoned()
            else -> Unit
        }
    }

    if (match?.status == MatchStatus.COMPLETED || match?.status == MatchStatus.ABANDONED) {
        return
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (match == null || userId == null) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

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
        val awaitingNextRound = pendingOutcome != null && openRound != null
        val showMovePicker = !uiState.hasSubmittedMove && !uiState.isSubmitting && when {
            drawReplay != null -> true
            awaitingNextRound -> true
            showDrawReveal || showOutcomeReveal -> false
            else -> true
        }
        val pickPrompt = when {
            uiState.hasSubmittedMove -> null
            drawReplay != null ->
                "Pick your move to replay the round (${GameRules.ROUND_TIMEOUT_SECONDS}s limit)"
            awaitingNextRound ->
                "Pick your move for round ${openRound!!.roundNumber} (${GameRules.ROUND_TIMEOUT_SECONDS}s limit)"
            showMovePicker ->
                "Pick your move — ${GameRules.ROUND_TIMEOUT_SECONDS}s per round"
            else -> null
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "vs ${match.opponentName(userId)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Round ${match.currentRound}  •  Best of 3",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScoreColumn(
                        label = "You",
                        score = match.myWins(userId),
                        progressColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ScoreColumn(
                        label = "Opponent",
                        score = match.opponentWins(userId),
                        progressColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }

        when {
            showDrawReveal || drawReplay != null -> {
                val round = if (showDrawReveal) currentRound!! else drawReplay!!
                val (myChoice, oppChoice) = round.choicesFor(userId, match)
                Spacer(modifier = Modifier.height(16.dp))
                DrawRoundBanner(
                    myChoice = myChoice,
                    opponentChoice = oppChoice,
                    isReplay = drawReplay != null,
                )
            }

            showOutcomeReveal || pendingOutcome != null -> {
                val round = if (showOutcomeReveal) currentRound!! else pendingOutcome!!
                val (myChoice, oppChoice) = round.choicesFor(userId, match)
                val wonRound = round.winner == userId
                Spacer(modifier = Modifier.height(16.dp))
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
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.hasSubmittedMove && myLockedChoice != null -> {
                    WaitingForOpponentCard(myChoice = myLockedChoice)
                }

                uiState.isSubmitting -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }

                showMovePicker -> {
                    pickPrompt?.let { prompt ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    MovePicker(
                        isSubmitting = uiState.isSubmitting,
                        onMove = viewModel::submitMove,
                    )
                }
            }
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
private fun ScoreColumn(
    label: String,
    score: Int,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(min = 72.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MatchWinProgressBar(
            wins = score,
            fillColor = progressColor,
        )
    }
}

@Composable
private fun MatchWinProgressBar(
    wins: Int,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(GameRules.WINS_TO_FINISH) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < wins) fillColor else trackColor),
            )
        }
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
