package com.rpsonline.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.LeaderboardEntry

private val SectionTitleHeight = 28.dp
private val RowHeight = 40.dp
private val RowSpacing = 6.dp
private const val VisibleRowCount = 3

/** Minimum vertical space needed to show the title plus three compact rows. */
fun minHeightForTopPlayersSection(): Dp =
    SectionTitleHeight + (RowHeight * VisibleRowCount) + (RowSpacing * (VisibleRowCount - 1))

@Composable
fun HomeTopPlayersSection(
    entries: List<LeaderboardEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Top Players",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(RowSpacing),
            ) {
                entries.take(VisibleRowCount).forEachIndexed { index, entry ->
                    HomeTopPlayerRow(rank = index + 1, entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HomeTopPlayerRow(
    rank: Int,
    entry: LeaderboardEntry,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$rank ${entry.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.elo}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
