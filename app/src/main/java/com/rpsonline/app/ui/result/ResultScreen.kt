package com.rpsonline.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundRecap
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.ui.components.rpsScreenPadding

@Composable
fun ResultScreen(
    matchId: String,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    var match by remember { mutableStateOf<Match?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(matchId) {
        match = matchRepository.getMatch(matchId)
        isLoading = false
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLoading || match == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        val userId = authRepository.currentUserId
        val currentMatch = match!!
        val myWins = userId?.let { currentMatch.myWins(it) } ?: 0
        val opponentWins = userId?.let { currentMatch.opponentWins(it) } ?: 0
        val won = userId != null && currentMatch.winnerId == userId
        val isDraw = userId != null &&
            currentMatch.winnerId == null &&
            myWins == opponentWins
        val eloDelta = userId?.let { currentMatch.myEloDelta(it) } ?: 0
        val opponentName = userId?.let { currentMatch.opponentName(it) } ?: "Opponent"
        val recaps = userId?.let { currentMatch.resolvedRoundRecaps(it) } ?: emptyList()
        val lastRound = recaps.lastOrNull()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (isDraw) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Balance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "Draw",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = "Match tied — no winner",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        } else {
            Text(
                text = if (won) "Victory!" else "Defeat",
                style = MaterialTheme.typography.displaySmall,
                color = if (won) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = "vs $opponentName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Final score: $myWins - $opponentWins")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ELO ${if (eloDelta >= 0) "+" else ""}$eloDelta",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        if (lastRound != null) {
            Spacer(modifier = Modifier.height(16.dp))
            LastRoundCard(lastRound = lastRound)
        }

        if (recaps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            MatchRecapCard(recaps = recaps)
        }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onPlayAgain,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Play Again")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Home")
        }
    }
}

@Composable
private fun LastRoundCard(lastRound: RoundRecap) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            RoundRecapRow(
                roundLabel = "Final round",
                recap = lastRound,
            )
        }
    }
}

@Composable
private fun MatchRecapCard(recaps: List<RoundRecap>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Match recap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            recaps.forEachIndexed { index, recap ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                RoundRecapRow(
                    roundLabel = "Round ${recap.roundNumber}",
                    recap = recap,
                )
            }
        }
    }
}

@Composable
private fun RoundRecapRow(
    roundLabel: String,
    recap: RoundRecap,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roundLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = recapChoicesLine(recap),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = recapOutcomeLabel(recap),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = recapOutcomeColor(recap),
        )
    }
}

private fun formatChoice(choice: String?): String =
    choice?.let { Move.fromString(it)?.label ?: it.lowercase() } ?: "—"

private fun formatChoices(myChoice: String?, opponentChoice: String?): String =
    "${formatChoice(myChoice)} vs ${formatChoice(opponentChoice)}"

private fun recapChoicesLine(recap: RoundRecap): String = when {
    recap.isDraw && recap.myChoice == null && recap.opponentChoice == null ->
        "No picks — round replayed"
    recap.isDraw -> "${formatChoices(recap.myChoice, recap.opponentChoice)} — same move"
    else -> formatChoices(recap.myChoice, recap.opponentChoice)
}

private fun recapOutcomeLabel(recap: RoundRecap): String = when {
    recap.isDraw || recap.won == null -> "Draw"
    recap.opponentTimedOut -> "Win (timeout)"
    recap.iTimedOut -> "Loss (timeout)"
    recap.won -> "Win"
    else -> "Loss"
}

@Composable
private fun recapOutcomeColor(recap: RoundRecap): androidx.compose.ui.graphics.Color = when {
    recap.isDraw || recap.won == null -> MaterialTheme.colorScheme.tertiary
    recap.won -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}
