package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.rpsonline.app.data.model.ViewerMatchResolution
import com.rpsonline.app.domain.MatchMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CompactMatchIdLength = 8
private val MatchHeaderCompactBreakpoint = 360.dp
private val MatchResultCompactBreakpoint = 116.dp

private val MatchDateTimeFormat = DateTimeFormatter.ofPattern("MMM d HH:mm")
private val MatchDateTimeCompactFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

@Composable
fun MatchHistoryCardHeader(
    entry: MatchHistoryEntry,
    lastActivityAt: Long,
    modifier: Modifier = Modifier,
) {
    MatchHistoryCardHeaderContent(
        entry = entry,
        lastActivityAt = lastActivityAt,
        resolution = entry.resolution,
        modifier = modifier,
    )
}

@Composable
private fun MatchHistoryCardHeaderContent(
    entry: MatchHistoryEntry,
    lastActivityAt: Long,
    resolution: ViewerMatchResolution,
    modifier: Modifier = Modifier,
) {
    val outcomeLabel = viewerMatchResolutionLabel(resolution)
    val outcomeColor = viewerMatchResolutionColor(resolution)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val playerNameStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val playerNameColor = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactHeader = maxWidth < MatchHeaderCompactBreakpoint

        Column(
            modifier = Modifier.fillMaxWidth(),
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
                        text = formatCompactMatchLabel(entry.matchId, entry.matchMode),
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
                        compact = compactHeader,
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
                compact = compactHeader,
            )
        }
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
    compact: Boolean,
) {
    val eloStyle = MaterialTheme.typography.labelSmall
    val vsPadding = if (compact) 4.dp else 6.dp

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
            modifier = Modifier.padding(horizontal = vsPadding),
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
    eloDelta: Int?,
) {
    val resultLineStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val scoreStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val scoreText = formatMatchScore(myWins, opponentWins)
    val resultGap = 6.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < MatchResultCompactBreakpoint) {
            Text(
                text = formatMatchResultLine(outcomeLabel, myWins, opponentWins, eloDelta),
                modifier = Modifier.fillMaxWidth(),
                style = resultLineStyle,
                color = outcomeColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = outcomeLabel,
                        style = resultLineStyle,
                        color = outcomeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = resultGap),
                    )
                }
                Text(
                    text = scoreText,
                    style = scoreStyle,
                    color = outcomeColor,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (eloDelta != null) {
                        Text(
                            text = "(${formatEloDelta(eloDelta)})",
                            style = resultLineStyle,
                            color = outcomeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = resultGap),
                        )
                    }
                }
            }
        }
    }
}

private fun formatCompactMatchLabel(matchId: String, matchMode: MatchMode): String =
    "${matchMode.name} #${matchId.take(CompactMatchIdLength)}"

@Composable
private fun MatchDateLabel(
    lastActivityAt: Long,
    color: androidx.compose.ui.graphics.Color,
    compact: Boolean,
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
    val format = if (compact) MatchDateTimeCompactFormat else MatchDateTimeFormat
    Text(
        text = zoned.format(format),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
