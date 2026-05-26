package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundRecap

private val RecapMoveIconSize = 14.dp
private val RecapCompactBreakpoint = 360.dp
/** Minimum width per recap column (~8 characters). */
private val RecapColumnMinWidthWide = 88.dp
private val RecapColumnMinWidthCompact = 72.dp
private val RecapRowColumnSpacing = 6.dp

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
    title: String? = "Match recap",
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    if (recaps.isEmpty()) return
    if (embedded) {
        MatchRecapContent(
            recaps = recaps,
            title = title,
            embedded = true,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        RpsCard(modifier = modifier.fillMaxWidth()) {
            MatchRecapContent(
                recaps = recaps,
                title = title,
                embedded = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MatchRecapContent(
    recaps: List<RoundRecap>,
    title: String?,
    embedded: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayRecaps = recaps.asReversed()
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < RecapCompactBreakpoint
        val horizontalPadding = when {
            embedded -> 0.dp
            compact -> 8.dp
            else -> 10.dp
        }
        val verticalPadding = if (embedded) 4.dp else 8.dp
        val columnMinWidth = if (compact) {
            RecapColumnMinWidthCompact
        } else {
            RecapColumnMinWidthWide
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            displayRecaps.forEachIndexed { index, recap ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                RoundRecapRow(
                    roundLabel = roundRecapLabel(recap.roundNumber),
                    recap = recap,
                    columnMinWidth = columnMinWidth,
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
    columnMinWidth: Dp = RecapColumnMinWidthWide,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RecapRowColumnSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecapRowColumn(
            modifier = Modifier.weight(1f),
            columnMinWidth = columnMinWidth,
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = roundLabel,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RecapRowColumn(
            modifier = Modifier.weight(1f),
            columnMinWidth = columnMinWidth,
            contentAlignment = Alignment.Center,
        ) {
            RoundChoicesLine(recap = recap)
        }
        RecapRowColumn(
            modifier = Modifier.weight(1f),
            columnMinWidth = columnMinWidth,
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = recapOutcomeColumnLabel(recap),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = recapOutcomeColor(recap),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecapRowColumn(
    columnMinWidth: Dp,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.widthIn(min = columnMinWidth),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
private fun RoundChoicesLine(recap: RoundRecap) {
    if (recap.isDraw && recap.myChoice == null && recap.opponentChoice == null) {
        Text(
            text = "Replayed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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

private fun roundRecapLabel(roundNumber: Int): String = "Round#$roundNumber"

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

/** Compact outcome text for fixed-width recap columns. */
private fun recapOutcomeColumnLabel(recap: RoundRecap): String = when {
    recap.isDraw || recap.won == null -> "Draw"
    recap.opponentTimedOut -> "Win TO"
    recap.iTimedOut -> "Loss TO"
    recap.won -> "Win"
    else -> "Loss"
}

@Composable
fun recapOutcomeColor(recap: RoundRecap): Color = when {
    recap.isDraw || recap.won == null -> MaterialTheme.colorScheme.tertiary
    recap.won -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}
