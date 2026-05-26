package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.MatchHistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CompactMatchIdLength = 8

private val MatchDateDayFormat = DateTimeFormatter.ofPattern("MMM d")
private val MatchTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun MatchHistoryCardHeader(
    entry: MatchHistoryEntry,
    lastActivityAt: Long,
    outcomeLabel: String,
    outcomeColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val playerNameStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val playerNameColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = formatCompactMatchLabel(entry.matchId),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier.weight(1.15f),
                contentAlignment = Alignment.TopCenter,
            ) {
                MatchResultCenter(
                    outcomeLabel = outcomeLabel,
                    outcomeColor = outcomeColor,
                    myWins = entry.myWins,
                    opponentWins = entry.opponentWins,
                    myElo = entry.myElo,
                    eloDelta = entry.eloDelta,
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopEnd,
            ) {
                MatchDateLabel(
                    lastActivityAt = lastActivityAt,
                    color = mutedColor,
                    modifier = Modifier.wrapContentWidth(Alignment.End),
                )
            }
        }

        MatchupBottomRow(
            myDisplayName = entry.myDisplayName,
            opponentName = entry.opponentName,
            myElo = entry.myElo,
            opponentElo = entry.opponentElo,
            nameStyle = playerNameStyle,
            nameColor = playerNameColor,
            vsColor = mutedColor,
        )
    }
}

@Composable
private fun MatchupBottomRow(
    myDisplayName: String,
    opponentName: String,
    myElo: Int?,
    opponentElo: Int?,
    nameStyle: androidx.compose.ui.text.TextStyle,
    nameColor: androidx.compose.ui.graphics.Color,
    vsColor: androidx.compose.ui.graphics.Color,
) {
    val eloStyle = MaterialTheme.typography.labelSmall

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (myElo != null) {
                EloRatingText(elo = myElo, style = eloStyle)
            }
            Text(
                text = myDisplayName.trim(),
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(start = if (myElo != null) 4.dp else 0.dp),
                style = nameStyle,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "vs",
            style = nameStyle,
            color = vsColor,
            modifier = Modifier.padding(horizontal = 6.dp),
            maxLines = 1,
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = opponentName.trim(),
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(end = if (opponentElo != null) 4.dp else 0.dp),
                style = nameStyle,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (opponentElo != null) {
                EloRatingText(elo = opponentElo, style = eloStyle)
            }
        }
    }
}

@Composable
private fun MatchResultCenter(
    outcomeLabel: String,
    outcomeColor: androidx.compose.ui.graphics.Color,
    myWins: Int,
    opponentWins: Int,
    myElo: Int?,
    eloDelta: Int?,
) {
    val scoreColor = outcomeColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "$myWins–$opponentWins",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scoreColor,
            maxLines = 1,
        )
        val resultLineStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = outcomeLabel,
                style = resultLineStyle,
                color = outcomeColor,
                maxLines = 1,
            )
            if (eloDelta != null) {
                Spacer(modifier = Modifier.width(4.dp))
                PostMatchEloSummary(
                    postMatchElo = postMatchElo(myElo, eloDelta),
                    eloDelta = eloDelta,
                    textStyle = resultLineStyle,
                )
            }
        }
    }
}

@Composable
private fun PostMatchEloSummary(
    postMatchElo: Int?,
    eloDelta: Int?,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    if (eloDelta == null) return

    val deltaColor = when {
        eloDelta > 0 -> MaterialTheme.colorScheme.primary
        eloDelta < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (postMatchElo != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EloRatingText(
                elo = postMatchElo,
                style = textStyle.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "(${formatEloDelta(eloDelta)})",
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = deltaColor,
                maxLines = 1,
            )
        }
    } else {
        Text(
            text = formatEloDelta(eloDelta),
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = deltaColor,
            maxLines = 1,
        )
    }
}

private fun formatCompactMatchLabel(matchId: String): String =
    "#${matchId.take(CompactMatchIdLength)}"

@Composable
private fun MatchDateLabel(
    lastActivityAt: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    if (lastActivityAt <= 0L) {
        Text(
            text = "—",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.End,
        )
        return
    }

    val zoned = Instant.ofEpochMilli(lastActivityAt).atZone(ZoneId.systemDefault())
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = zoned.format(MatchDateDayFormat),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
        Text(
            text = zoned.format(MatchTimeFormat),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
