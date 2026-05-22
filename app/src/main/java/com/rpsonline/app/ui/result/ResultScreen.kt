package com.rpsonline.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isLoading || match == null) {
            CircularProgressIndicator()
            return
        }

        val userId = authRepository.currentUserId
        val currentMatch = match!!
        val myWins = userId?.let { currentMatch.myWins(it) } ?: 0
        val opponentWins = userId?.let { currentMatch.opponentWins(it) } ?: 0
        val won = userId != null &&
            currentMatch.winnerId == userId &&
            myWins > opponentWins
        val isDraw = myWins == opponentWins
        val eloDelta = userId?.let { currentMatch.myEloDelta(it) } ?: 0

        val headline = when {
            won -> "Victory!"
            isDraw -> "Draw"
            else -> "Defeat"
        }

        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = when {
                won -> MaterialTheme.colorScheme.primary
                isDraw -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.error
            },
        )
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

        Spacer(modifier = Modifier.height(24.dp))
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
