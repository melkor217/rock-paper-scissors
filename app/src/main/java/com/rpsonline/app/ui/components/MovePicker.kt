package com.rpsonline.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move

@Composable
fun MovePicker(
    isSubmitting: Boolean,
    onMove: (Move) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (isSubmitting) {
        CircularProgressIndicator(modifier = modifier)
        return
    }

    val spacing = if (compact) 6.dp else 10.dp
    val cardHeight = if (compact) 72.dp else 84.dp
    val labelSpacing = if (compact) 4.dp else 6.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Move.entries.forEach { move ->
            MoveChoiceColumn(
                move = move,
                onClick = { onMove(move) },
                compact = compact,
                cardHeight = cardHeight,
                labelSpacing = labelSpacing,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MoveChoiceColumn(
    move: Move,
    onClick: () -> Unit,
    compact: Boolean,
    cardHeight: androidx.compose.ui.unit.Dp,
    labelSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val style = moveCardStyle(move)
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val iconSize = if (compact) 36.dp else 44.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .shadow(if (compact) 2.dp else 3.dp, shape)
                .clip(shape)
                .background(style.gradient)
                .border(BorderStroke(1.dp, style.borderColor), shape)
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = move.icon,
                contentDescription = move.label,
                tint = style.labelColor,
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.Center),
            )
        }
        Text(
            text = move.label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = style.labelColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = labelSpacing),
        )
    }
}

private val Move.icon: ImageVector
    get() = when (this) {
        Move.ROCK -> Icons.Default.Landscape
        Move.PAPER -> Icons.Default.Description
        Move.SCISSORS -> Icons.Default.ContentCut
    }

private data class MoveCardStyle(
    val gradient: Brush,
    val labelColor: Color,
    val borderColor: Color,
)

@Composable
private fun moveCardStyle(move: Move): MoveCardStyle {
    val scheme = MaterialTheme.colorScheme
    return when (move) {
        Move.ROCK -> MoveCardStyle(
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4A5568),
                    Color(0xFF2A3344),
                    Color(0xFF12182A),
                ),
            ),
            labelColor = scheme.primary,
            borderColor = scheme.primary.copy(alpha = 0.75f),
        )
        Move.PAPER -> MoveCardStyle(
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF5A2868),
                    Color(0xFF3A1848),
                    Color(0xFF1A0828),
                ),
            ),
            labelColor = scheme.secondary,
            borderColor = scheme.secondary.copy(alpha = 0.75f),
        )
        Move.SCISSORS -> MoveCardStyle(
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6A5A10),
                    Color(0xFF4A3A08),
                    Color(0xFF2A2000),
                ),
            ),
            labelColor = scheme.tertiary,
            borderColor = scheme.tertiary.copy(alpha = 0.75f),
        )
    }
}
