package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (myElo != null) {
            Text(
                text = myDisplayName,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = " ",
                style = labelStyle,
            )
            EloRatingText(
                elo = myElo,
                style = valueStyle,
            )
            Text(
                text = " vs ",
                style = labelStyle,
            )
        } else {
            Text(
                text = "vs ",
                style = labelStyle,
            )
        }
        Text(
            text = opponentName,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (myElo != null) Modifier else Modifier.weight(1f, fill = false),
        )
        opponentElo?.let { elo ->
            Text(
                text = " ",
                style = labelStyle,
            )
            EloRatingText(
                elo = elo,
                style = valueStyle,
            )
        }
    }
}
