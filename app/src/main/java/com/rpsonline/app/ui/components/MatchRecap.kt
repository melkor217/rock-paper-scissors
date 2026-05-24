package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundRecap

@Composable
fun MatchRecapCard(
    recaps: List<RoundRecap>,
    title: String = "Match recap",
    modifier: Modifier = Modifier,
) {
    if (recaps.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            recaps.forEachIndexed { index, recap ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                RoundRecapRow(
                    roundLabel = if (index == recaps.lastIndex) {
                        "Final round"
                    } else {
                        "Round ${recap.roundNumber}"
                    },
                    recap = recap,
                )
            }
        }
    }
}

@Composable
fun RoundRecapRow(
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
                style = MaterialTheme.typography.bodyMedium,
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

fun recapChoicesLine(recap: RoundRecap): String = when {
    recap.isDraw && recap.myChoice == null && recap.opponentChoice == null ->
        "No picks — round replayed"
    recap.isDraw -> "${formatChoices(recap.myChoice, recap.opponentChoice)} — same move"
    else -> formatChoices(recap.myChoice, recap.opponentChoice)
}

fun recapOutcomeLabel(recap: RoundRecap): String = when {
    recap.isDraw || recap.won == null -> "Draw"
    recap.opponentTimedOut -> "Win (timeout)"
    recap.iTimedOut -> "Loss (timeout)"
    recap.won -> "Win"
    else -> "Loss"
}

@Composable
fun recapOutcomeColor(recap: RoundRecap): Color = when {
    recap.isDraw || recap.won == null -> MaterialTheme.colorScheme.tertiary
    recap.won -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}
