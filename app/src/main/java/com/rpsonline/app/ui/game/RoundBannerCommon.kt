package com.rpsonline.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.rpsonline.app.data.model.Move

enum class RoundBannerKind {
    Win,
    Lose,
    Draw,
}

fun roundBannerHeadline(kind: RoundBannerKind, roundNumber: Int): String = when (kind) {
    RoundBannerKind.Win -> "You won Round #$roundNumber"
    RoundBannerKind.Lose -> "You lost Round #$roundNumber"
    RoundBannerKind.Draw -> "Draw on Round #$roundNumber"
}

fun roundBannerSubtitle(
    kind: RoundBannerKind,
    compact: Boolean,
    showFollowUpHint: Boolean,
): String = when (kind) {
    RoundBannerKind.Draw -> when {
        compact && showFollowUpHint -> "Replay this round."
        compact -> "No point awarded."
        showFollowUpHint -> "Replay this round. Score unchanged."
        else -> "No point awarded."
    }
    RoundBannerKind.Win -> roundBannerScoredSubtitle("Point scored", compact, showFollowUpHint)
    RoundBannerKind.Lose -> roundBannerScoredSubtitle("Opponent scored", compact, showFollowUpHint)
}

private fun roundBannerScoredSubtitle(
    base: String,
    compact: Boolean,
    showFollowUpHint: Boolean,
): String = when {
    compact && showFollowUpHint -> ""
    compact -> "$base."
    showFollowUpHint -> "$base — pick your move for the next round."
    else -> "$base."
}

@Composable
fun RoundOutcomeBanner(
    kind: RoundBannerKind,
    roundNumber: Int,
    myChoice: String?,
    opponentChoice: String?,
    showFollowUpHint: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor, icon) = when (kind) {
        RoundBannerKind.Win -> Triple(
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer,
            Icons.Default.EmojiEvents,
        )
        RoundBannerKind.Lose -> Triple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            Icons.Default.HeartBroken,
        )
        RoundBannerKind.Draw -> Triple(
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer,
            Icons.Default.Balance,
        )
    }
    RoundOutcomeCard(
        containerColor = containerColor,
        contentColor = contentColor,
        icon = icon,
        headline = roundBannerHeadline(kind, roundNumber),
        subtitle = roundBannerSubtitle(kind, compact, showFollowUpHint),
        myChoice = myChoice,
        opponentChoice = opponentChoice,
        choiceSeparator = if (kind == RoundBannerKind.Draw) "=" else "vs",
        modifier = modifier,
        compact = compact,
        opponentLabel = opponentLabel,
    )
}

@Composable
fun RoundOutcomeCard(
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    headline: String,
    subtitle: String,
    myChoice: String?,
    opponentChoice: String?,
    choiceSeparator: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    opponentLabel: String = "Opponent",
) {
    val horizontalPadding = if (compact) 12.dp else 20.dp
    val verticalPadding = if (compact) 10.dp else 16.dp
    val contentSpacing = if (compact) 6.dp else 12.dp

    if (compact) {
        val shape = MaterialTheme.shapes.medium
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(containerColor)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            RoundOutcomeHeader(
                icon = icon,
                headline = headline,
                contentColor = contentColor,
                compact = true,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = compactBannerTextStyle(MaterialTheme.typography.bodySmall),
                    color = contentColor,
                )
            }
            if (myChoice != null && opponentChoice != null) {
                Text(
                    text = compactChoicesLine(
                        myChoice = myChoice,
                        opponentChoice = opponentChoice,
                        choiceSeparator = choiceSeparator,
                        opponentLabel = opponentLabel,
                    ),
                    style = compactBannerTextStyle(MaterialTheme.typography.labelLarge),
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            RoundOutcomeHeader(
                icon = icon,
                headline = headline,
                contentColor = contentColor,
                compact = false,
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }

            if (myChoice != null && opponentChoice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceChip(label = "You", choice = myChoice, color = contentColor)
                    Text(
                        text = choiceSeparator,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                    )
                    ChoiceChip(label = opponentLabel, choice = opponentChoice, color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun RoundOutcomeHeader(
    icon: ImageVector,
    headline: String,
    contentColor: Color,
    compact: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = if (compact) Modifier.size(20.dp) else Modifier,
        )
        Text(
            text = headline,
            style = if (compact) {
                compactBannerTextStyle(MaterialTheme.typography.titleMedium)
            } else {
                MaterialTheme.typography.titleLarge
            },
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

private fun compactChoicesLine(
    myChoice: String,
    opponentChoice: String,
    choiceSeparator: String,
    opponentLabel: String,
): String =
  "You ${formatChoice(myChoice)} $choiceSeparator $opponentLabel ${formatChoice(opponentChoice)}"

private fun formatChoice(choice: String): String =
    Move.fromString(choice)?.label ?: choice.lowercase()

private fun compactBannerTextStyle(
    base: androidx.compose.ui.text.TextStyle,
) = base.copy(
    lineHeight = 1.15.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun ChoiceChip(label: String, choice: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        Text(
            text = formatChoice(choice),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
