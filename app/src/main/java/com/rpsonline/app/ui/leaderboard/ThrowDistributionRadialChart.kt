package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private data class MoveAxis(
    val icon: ImageVector,
    val label: String,
    val angleDeg: Float,
)

private val MoveAxes = listOf(
    MoveAxis(Icons.Default.Landscape, "Rock", -90f),
    MoveAxis(Icons.Default.Description, "Paper", 30f),
    MoveAxis(Icons.Default.ContentCut, "Scissors", 150f),
)

private const val GridRings = 4

/** Shared radii/strokes so bars, grid, and icons stay aligned. */
private data class RadialChartLayout(
    val iconOrbitPx: Float,
    val innerRadiusPx: Float,
    val barMaxRadiusPx: Float,
    val barSpanPx: Float,
    val barStrokePx: Float,
)

private fun axisPoint(center: Offset, angleRad: Float, radius: Float): Offset =
    Offset(
        x = center.x + cos(angleRad) * radius,
        y = center.y + sin(angleRad) * radius,
    )

private fun radialChartLayout(chartSizePx: Float, iconSizePx: Float, barStrokePx: Float): RadialChartLayout {
    val half = chartSizePx / 2f
    val iconOrbit = half * 0.9f
    val iconInnerEdge = iconOrbit - iconSizePx / 2f
    val barGap = iconSizePx * 0.28f + barStrokePx / 2f
    val barMaxRadius = iconInnerEdge - barGap
    val innerRadius = barMaxRadius * 0.08f
    return RadialChartLayout(
        iconOrbitPx = iconOrbit,
        innerRadiusPx = innerRadius,
        barMaxRadiusPx = barMaxRadius,
        barSpanPx = barMaxRadius - innerRadius,
        barStrokePx = barStrokePx,
    )
}

@Composable
fun ThrowDistributionRadialChart(
    rock: Int,
    paper: Int,
    scissors: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val counts = intArrayOf(rock, paper, scissors)
    val totalThrows = counts.sum()
    val maxCount = max(counts.max(), 1)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val triangleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val triangleStroke = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val iconSize = 8.dp
    val barStroke = 2.5.dp
    val iconSizePx = with(density) { iconSize.toPx() }
    val chartSizePx = with(density) { size.toPx() }
    val barStrokePx = with(density) { barStroke.toPx() }
    val layout = radialChartLayout(chartSizePx, iconSizePx, barStrokePx)
    val neutralBar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val barColors = counts.map { count ->
        if (totalThrows <= 0 || count <= 0) {
            neutralBar
        } else {
            leaderboardSpectrumColor(count * 100f / totalThrows)
        }
    }

    val description = buildString {
        append("Throws: rock $rock, paper $paper, scissors $scissors")
        append(". Bar length by most-used move; color by share (33% yellow).")
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            for (ring in 1..GridRings) {
                val radius = layout.innerRadiusPx + layout.barSpanPx * (ring.toFloat() / GridRings)
                drawCircle(
                    color = gridColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1f),
                )
            }

            val barEnds = MoveAxes.mapIndexed { index, axis ->
                val angleRad = axis.angleDeg * (PI.toFloat() / 180f)
                val barLength = layout.barSpanPx * (counts[index].toFloat() / maxCount)
                axisPoint(center, angleRad, layout.innerRadiusPx + barLength)
            }

            val triangle = Path().apply {
                moveTo(barEnds[0].x, barEnds[0].y)
                lineTo(barEnds[1].x, barEnds[1].y)
                lineTo(barEnds[2].x, barEnds[2].y)
                close()
            }
            drawPath(path = triangle, color = triangleFill)
            drawPath(
                path = triangle,
                color = triangleStroke,
                style = Stroke(width = 1f),
            )

            counts.forEachIndexed { index, count ->
                if (count <= 0) return@forEachIndexed
                val angleRad = MoveAxes[index].angleDeg * (PI.toFloat() / 180f)
                val start = axisPoint(center, angleRad, layout.innerRadiusPx)
                drawLine(
                    color = barColors[index],
                    start = start,
                    end = barEnds[index],
                    strokeWidth = layout.barStrokePx,
                    cap = StrokeCap.Round,
                )
            }
        }

        MoveAxes.forEach { axis ->
            val angleRad = axis.angleDeg * (PI.toFloat() / 180f)
            Icon(
                imageVector = axis.icon,
                contentDescription = axis.label,
                tint = iconTint,
                modifier = Modifier
                    .size(iconSize)
                    .offset {
                        IntOffset(
                            (cos(angleRad) * layout.iconOrbitPx).roundToInt(),
                            (sin(angleRad) * layout.iconOrbitPx).roundToInt(),
                        )
                    },
            )
        }
    }
}
