package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared rock/paper/scissors icon size for compact stat rows. */
val MoveStatIconSize = 14.dp

@Composable
fun ThrowCountRow(
    rock: Int,
    paper: Int,
    scissors: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = MoveStatIconSize,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(14.dp),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThrowCountChip(
            icon = Icons.Default.Landscape,
            label = "Rock",
            count = rock,
            iconSize = iconSize,
            textStyle = textStyle,
        )
        ThrowCountChip(
            icon = Icons.Default.Description,
            label = "Paper",
            count = paper,
            iconSize = iconSize,
            textStyle = textStyle,
        )
        ThrowCountChip(
            icon = Icons.Default.ContentCut,
            label = "Scissors",
            count = scissors,
            iconSize = iconSize,
            textStyle = textStyle,
        )
    }
}

@Composable
private fun ThrowCountChip(
    icon: ImageVector,
    label: String,
    count: Int,
    iconSize: Dp,
    textStyle: TextStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = "$count",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
