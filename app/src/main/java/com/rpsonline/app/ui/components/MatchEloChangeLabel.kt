package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

fun formatEloDelta(delta: Int): String =
    if (delta >= 0) "+$delta" else "$delta"

fun postMatchElo(preMatchElo: Int?, eloDelta: Int?): Int? {
    if (preMatchElo == null || eloDelta == null) return null
    return preMatchElo + eloDelta
}

@Composable
fun MatchEloChangeLabel(
    postMatchElo: Int?,
    eloDelta: Int?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    if (eloDelta == null) return

    val deltaColor = when {
        eloDelta > 0 -> MaterialTheme.colorScheme.primary
        eloDelta < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (postMatchElo != null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ELO ",
                style = textStyle,
                fontWeight = FontWeight.Bold,
            )
            EloRatingText(
                elo = postMatchElo,
                style = textStyle.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = " (${formatEloDelta(eloDelta)})",
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = deltaColor,
            )
        }
    } else {
        Text(
            text = "ELO ${formatEloDelta(eloDelta)}",
            modifier = modifier,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = deltaColor,
        )
    }
}
