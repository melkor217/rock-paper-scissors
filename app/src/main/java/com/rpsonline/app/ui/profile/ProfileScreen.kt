package com.rpsonline.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.ui.components.MatchRecapCard
import com.rpsonline.app.ui.components.ThrowCountRow
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.leaderboard.leaderboardSpectrumColor
import com.rpsonline.app.viewmodel.ProfileViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MatchDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")

@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    Column(modifier = Modifier.rpsScreenPadding()) {
        Text(
            text = uiState.profile?.displayName ?: "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val profile = uiState.profile
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        ProfileStatsCard(
                            displayName = profile?.displayName ?: "Player",
                            elo = profile?.elo ?: 1000,
                            wins = profile?.wins ?: 0,
                            losses = profile?.losses ?: 0,
                            throwsRock = profile?.throwsRock ?: 0,
                            throwsPaper = profile?.throwsPaper ?: 0,
                            throwsScissors = profile?.throwsScissors ?: 0,
                        )
                    }
                    item {
                        Text(
                            text = if (uiState.isOwnProfile) {
                                "Last 10 matches"
                            } else {
                                "Matches you played together"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (uiState.matchHistory.isEmpty()) {
                        item {
                            Text(
                                text = if (uiState.isOwnProfile) {
                                    "No completed matches yet."
                                } else {
                                    "No shared matches yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = uiState.matchHistory,
                            key = { it.matchId },
                        ) { entry ->
                            MatchHistoryCard(entry = entry)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ProfileStatsCard(
    displayName: String,
    elo: Int,
    wins: Int,
    losses: Int,
    throwsRock: Int,
    throwsPaper: Int,
    throwsScissors: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "ELO $elo",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Wins $wins · Losses $losses",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val games = wins + losses
            if (games > 0) {
                val winRate = (wins * 100) / games
                Text(
                    text = "Win rate $winRate%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = leaderboardSpectrumColor(winRate.toFloat()),
                )
            }
            ThrowCountRow(
                rock = throwsRock,
                paper = throwsPaper,
                scissors = throwsScissors,
            )
        }
    }
}

@Composable
private fun MatchHistoryCard(
    entry: MatchHistoryEntry,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatCompactMatchLabel(entry.matchId),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "vs ${entry.opponentName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatMatchDate(entry.lastActivityAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = matchOutcomeLabel(entry),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = matchOutcomeColor(entry),
                    )
                    Text(
                        text = "${entry.myWins} – ${entry.opponentWins}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    entry.eloDelta?.let { delta ->
                        Text(
                            text = "ELO ${if (delta >= 0) "+" else ""}$delta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                delta > 0 -> MaterialTheme.colorScheme.primary
                                delta < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            MatchRecapCard(
                recaps = entry.recaps,
                title = "Rounds",
            )
        }
    }
}

private const val CompactMatchIdLength = 8

private fun formatCompactMatchLabel(matchId: String): String {
    val compact = matchId.take(CompactMatchIdLength)
    return "Match#$compact"
}

private fun formatMatchDate(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(MatchDateFormat)
}

private fun matchOutcomeLabel(entry: MatchHistoryEntry): String = when {
    entry.isAbandoned -> "Cancelled"
    entry.isDraw -> "Draw"
    entry.won == true -> "Win"
    entry.won == false -> "Loss"
    else -> "—"
}

@Composable
private fun matchOutcomeColor(entry: MatchHistoryEntry): androidx.compose.ui.graphics.Color = when {
    entry.isAbandoned -> MaterialTheme.colorScheme.onSurfaceVariant
    entry.isDraw -> MaterialTheme.colorScheme.tertiary
    entry.won == true -> MaterialTheme.colorScheme.primary
    entry.won == false -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
