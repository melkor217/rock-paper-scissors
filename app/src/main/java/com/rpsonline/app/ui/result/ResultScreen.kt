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
import com.rpsonline.app.ui.components.HomeOutlinedButton
import com.rpsonline.app.ui.components.RpsCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.UserRepository
import com.rpsonline.app.domain.matchResultOutcomeDetail
import com.rpsonline.app.domain.opponentEloAtMatch
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.ui.components.MatchEloChangeLabel
import com.rpsonline.app.ui.components.formatMatchScore
import com.rpsonline.app.ui.components.formatMatchSeriesDetail
import com.rpsonline.app.ui.components.MatchRecapCard
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.PlayerStatsWidget
import com.rpsonline.app.ui.components.rpsScreenPadding

@Composable
fun ResultScreen(
    matchId: String,
    onPlayAgain: (MatchMode) -> Unit,
    onHome: () -> Unit,
    onOpponentProfile: (String) -> Unit,
) {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val userRepository = remember { UserRepository() }
    var match by remember { mutableStateOf<Match?>(null) }
    var myCurrentElo by remember { mutableStateOf<Int?>(null) }
    var opponentProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(matchId) {
        val userId = authRepository.currentUserId
        match = matchRepository.getMatch(matchId)
        myCurrentElo = userId?.let { userRepository.getUserProfile(it)?.elo }
        isLoading = false
        val opponentId = userId?.let { uid -> match?.opponentId(uid) } ?: return@LaunchedEffect

        launch {
            userRepository.observeUserProfile(opponentId).collectLatest { profile ->
                opponentProfile = profile
            }
        }

        // Cloud Functions may finish incrementing throw stats after the result screen opens.
        repeat(8) {
            delay(2_000)
            userRepository.getUserProfile(opponentId)?.let { opponentProfile = it }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.rpsScreenPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLoading || match == null) {
            RpsLoadingColumn(modifier = Modifier.fillMaxSize())
            return
        }

        val userId = authRepository.currentUserId
        val currentMatch = match!!
        val myWins = userId?.let { currentMatch.myWins(it) } ?: 0
        val opponentWins = userId?.let { currentMatch.opponentWins(it) } ?: 0
        val isCancelled = currentMatch.status == MatchStatus.ABANDONED
        val won = userId != null && currentMatch.winnerId == userId
        val isDraw = !isCancelled && userId != null &&
            currentMatch.winnerId == null &&
            myWins == opponentWins
        val eloDelta = userId?.let { currentMatch.myEloDelta(it) }
        val opponentName = userId?.let { currentMatch.opponentName(it) } ?: "Opponent"
        val opponentId = userId?.let { currentMatch.opponentId(it) }
        val opponentElo = userId?.let { uid ->
            myCurrentElo?.let { currentMatch.opponentEloAtMatch(uid, it) }
        }
        val recaps = userId?.let { currentMatch.resolvedRoundRecaps(it) } ?: emptyList()
        val outcomeDetail = matchResultOutcomeDetail(
            match = currentMatch,
            won = won,
            isDraw = isDraw,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (isCancelled) {
            RpsCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Cancelled",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "This match was cancelled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isDraw) {
            RpsCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
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
            outcomeDetail?.let { detail ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatMatchSeriesDetail(currentMatch.matchMode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FinalScoreCard(
            myWins = myWins,
            opponentWins = opponentWins,
            postMatchElo = myCurrentElo,
            eloDelta = eloDelta,
        )

        if (opponentId != null) {
            Spacer(modifier = Modifier.height(12.dp))
            PlayerStatsWidget(
                displayName = opponentProfile?.displayName ?: opponentName,
                profile = opponentProfile,
                eloOverride = opponentElo,
                onClick = { onOpponentProfile(opponentId) },
            )
        }

        if (recaps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            MatchRecapCard(recaps = recaps)
        }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onPlayAgain(currentMatch.matchMode) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Play Again")
        }
        Spacer(modifier = Modifier.height(8.dp))
        HomeOutlinedButton(onClick = onHome)
    }
}

@Composable
private fun FinalScoreCard(
    myWins: Int,
    opponentWins: Int,
    postMatchElo: Int?,
    eloDelta: Int?,
) {
    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Final score: ${formatMatchScore(myWins, opponentWins)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            MatchEloChangeLabel(
                postMatchElo = postMatchElo,
                eloDelta = eloDelta,
            )
        }
    }
}
