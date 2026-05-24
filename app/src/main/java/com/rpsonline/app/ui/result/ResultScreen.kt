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
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.ui.components.MatchRecapCard
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

        Spacer(modifier = Modifier.height(12.dp))

        FinalScoreCard(
            myWins = myWins,
            opponentWins = opponentWins,
            eloDelta = eloDelta,
        )

        if (recaps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
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
private fun FinalScoreCard(
    myWins: Int,
    opponentWins: Int,
    eloDelta: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Final score: $myWins – $opponentWins",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "ELO ${if (eloDelta >= 0) "+" else ""}$eloDelta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    eloDelta > 0 -> MaterialTheme.colorScheme.primary
                    eloDelta < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

