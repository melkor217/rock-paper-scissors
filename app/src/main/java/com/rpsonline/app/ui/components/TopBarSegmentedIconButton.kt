package com.rpsonline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of one top-bar segmented icon slot; four fit in the cutout right ear. */
val TopBarSegmentedIconButtonWidth = 20.dp

/** Uniform glyph size for all top-bar icon slots. */
val TopBarSegmentedIconGlyphSize = 12.dp

private val TopBarSegmentedIconCorner = RoundedCornerShape(5.dp)

/** Maximum glyph size when a top-bar icon slot expands on wide ears. */
private val TopBarSegmentedIconGlyphSizeMax = 18.dp

@Composable
fun TopBarSegmentedSlot(
    onClick: () -> Unit,
    active: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(
        litColor: Color,
        ghostColor: Color,
        contentSize: Dp,
    ) -> Unit,
) {
    val litColor = segmentedDisplayLitColor()
    val ghostColor = segmentedDisplayGhostColor()
    val borderColor = if (active) {
        litColor.copy(alpha = 0.48f)
    } else {
        lerp(ghostColor, MaterialTheme.colorScheme.outlineVariant, 0.45f).copy(alpha = 0.58f)
    }
    val fillColor = if (active) {
        litColor.copy(alpha = 0.14f)
    } else {
        ghostColor.copy(alpha = 0.52f)
    }

    BoxWithConstraints(
        modifier = modifier
            .defaultMinSize(
                minWidth = TopBarSegmentedIconButtonWidth,
                minHeight = SegmentedDisplayHeight,
            )
            .fillMaxWidth()
            .height(SegmentedDisplayHeight)
            .semantics { this.contentDescription = contentDescription }
            .border(width = 1.dp, color = borderColor, shape = TopBarSegmentedIconCorner)
            .background(fillColor, TopBarSegmentedIconCorner)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val contentSize = (minOf(maxWidth, maxHeight) * 0.82f)
            .coerceIn(TopBarSegmentedIconGlyphSize + 2.dp, TopBarSegmentedIconGlyphSizeMax + 4.dp)
        content(litColor, ghostColor, contentSize)
    }
}

/**
 * Compact icon control styled like a seven-segment digit slot (lit/ghost states).
 * Expands horizontally when the parent row assigns extra width (e.g. [Modifier.weight]).
 */
@Composable
fun TopBarSegmentedIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    TopBarSegmentedSlot(
        onClick = onClick,
        active = active,
        contentDescription = contentDescription,
        modifier = modifier,
    ) { litColor, ghostColor, glyphSize ->
        val iconTint = if (active) {
            litColor
    } else {
        lerp(ghostColor, litColor, 0.44f)
    }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(glyphSize),
        )
    }
}
