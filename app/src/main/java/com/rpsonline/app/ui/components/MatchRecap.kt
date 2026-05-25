package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundRecap

private val RecapMoveIconSize = 16.dp

private enum class RecapSideOutcome {
    Won,
    Lost,
    Draw,
}

@Composable
private fun recapSideColor(outcome: RecapSideOutcome): Color = when (outcome) {
    RecapSideOutcome.Won -> MaterialTheme.colorScheme.primary
    RecapSideOutcome.Lost -> MaterialTheme.colorScheme.error
    RecapSideOutcome.Draw -> MaterialTheme.colorScheme.tertiary
}

private fun mySideOutcome(recap: RoundRecap): RecapSideOutcome = when {
    recap.isDraw || recap.won == null -> RecapSideOutcome.Draw
    recap.won -> RecapSideOutcome.Won
    else -> RecapSideOutcome.Lost
}

private fun opponentSideOutcome(recap: RoundRecap): RecapSideOutcome = when {
    recap.isDraw || recap.won == null -> RecapSideOutcome.Draw
    recap.won -> RecapSideOutcome.Lost
    else -> RecapSideOutcome.Won
}

@Composable
fun MatchRecapCard(
    recaps: List<RoundRecap>,
    title: String = "Match recap",
    modifier: Modifier = Modifier,
) {
    if (recaps.isEmpty()) return
    val displayRecaps = recaps.asReversed()
    RpsCard(modifier = modifier.fillMaxWidth()) {
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
            displayRecaps.forEachIndexed { index, recap ->
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = roundLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoundChoicesLine(recap = recap)
        }
        Text(
            text = recapOutcomeLabel(recap),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = recapOutcomeColor(recap),
        )
    }
}

@Composable
private fun RoundChoicesLine(recap: RoundRecap) {
    if (recap.isDraw && recap.myChoice == null && recap.opponentChoice == null) {
        Text(
            text = "No picks — round replayed",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecapMoveIcon(
            choice = recap.myChoice,
            outcome = mySideOutcome(recap),
        )
        Text(
            text = recapChoicesSeparator(recap),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = recapSeparatorColor(recap),
        )
        RecapMoveIcon(
            choice = recap.opponentChoice,
            outcome = opponentSideOutcome(recap),
        )
    }
}

private fun recapChoicesSeparator(recap: RoundRecap): String = when {
    recap.isDraw || recap.won == null -> "="
    recap.won -> ">"
    else -> "<"
}

@Composable
private fun recapSeparatorColor(recap: RoundRecap): Color = when {
    recap.isDraw || recap.won == null -> MaterialTheme.colorScheme.tertiary
    recap.won -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun RecapMoveIcon(
    choice: String?,
    outcome: RecapSideOutcome,
    iconSize: Dp = RecapMoveIconSize,
) {
    val color = recapSideColor(outcome)
    val move = Move.fromString(choice)
    val icon = moveIcon(move)
    if (icon == null) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        return
    }
    Icon(
        imageVector = icon,
        contentDescription = move?.label,
        tint = color,
        modifier = Modifier.size(iconSize),
    )
}

private fun moveIcon(move: Move?): ImageVector? = when (move) {
    Move.ROCK -> Icons.Default.Landscape
    Move.PAPER -> Icons.Default.Description
    Move.SCISSORS -> Icons.Default.ContentCut
    null -> null
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
