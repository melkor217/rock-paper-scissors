package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

private const val SegmentCount = 3
private const val SegmentSweepDegrees = 360f / SegmentCount
private const val SegmentGapDegrees = 8f
private const val SegmentArcSweep = SegmentSweepDegrees - SegmentGapDegrees

@Composable
fun throwDistributionColors(): List<Color> {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        listOf(
            if (dark) Color(0xFFBCAAA4) else Color(0xFF6D4C41),
            if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0),
            if (dark) Color(0xFFFF8A80) else Color(0xFFC62828),
        )
    }
}

@Composable
fun ThrowDistributionRadialChart(
    rock: Int,
    paper: Int,
    scissors: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val counts = listOf(rock, paper, scissors)
    val maxCount = max(counts.maxOrNull() ?: 0, 1)
    val colors = throwDistributionColors()
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val strokeWidth = size.value * 0.16f

    val description = buildString {
        append("Throws: rock $rock, paper $paper, scissors $scissors")
        append(". Normalized to most-used move at 100%.")
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
        val topLeft = Offset(inset, inset)
        val startBase = -90f + SegmentGapDegrees / 2f

        counts.forEachIndexed { index, count ->
            val segmentStart = startBase + index * SegmentSweepDegrees
            val fillSweep = SegmentArcSweep * (count.toFloat() / maxCount)

            drawArc(
                color = trackColor,
                startAngle = segmentStart,
                sweepAngle = SegmentArcSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (fillSweep > 0f) {
                drawArc(
                    color = colors[index],
                    startAngle = segmentStart,
                    sweepAngle = fillSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}
