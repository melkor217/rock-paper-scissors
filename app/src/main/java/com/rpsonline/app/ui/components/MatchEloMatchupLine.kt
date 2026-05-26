package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val MaxMatchupNameLength = 22

@Composable
fun MatchEloMatchupLine(
    myDisplayName: String,
    opponentName: String,
    myElo: Int?,
    opponentElo: Int?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val labelStyle = textStyle.copy(fontWeight = FontWeight.SemiBold)
    val valueStyle = textStyle.copy(fontWeight = FontWeight.SemiBold)
    val nameColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (myElo != null) {
            MatchupPlayerRow(
                name = shortenMatchupName(myDisplayName),
                elo = myElo,
                labelStyle = labelStyle,
                valueStyle = valueStyle,
                nameColor = nameColor,
            )
        }
        OpponentMatchupRow(
            opponentName = shortenMatchupName(opponentName),
            opponentElo = opponentElo,
            showVsPrefix = myElo != null,
            labelStyle = labelStyle,
            valueStyle = valueStyle,
            nameColor = nameColor,
            mutedColor = mutedColor,
        )
    }
}

@Composable
private fun MatchupPlayerRow(
    name: String,
    elo: Int,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    nameColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = labelStyle,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        EloRatingText(
            elo = elo,
            style = valueStyle,
        )
    }
}

@Composable
private fun OpponentMatchupRow(
    opponentName: String,
    opponentElo: Int?,
    showVsPrefix: Boolean,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    nameColor: Color,
    mutedColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showVsPrefix) {
                Text(
                    text = "vs ",
                    style = labelStyle,
                    color = mutedColor,
                    maxLines = 1,
                )
            }
            Text(
                text = if (showVsPrefix) opponentName else "vs $opponentName",
                style = labelStyle,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        opponentElo?.let { elo ->
            EloRatingText(
                elo = elo,
                style = valueStyle,
            )
        }
    }
}

private fun shortenMatchupName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.length <= MaxMatchupNameLength) return trimmed
    return trimmed.take(MaxMatchupNameLength - 1) + "…"
}
