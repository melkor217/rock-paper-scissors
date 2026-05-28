package com.rpsonline.app.ui.profile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.domain.DailyEloDelta
import com.rpsonline.app.domain.weeklyEloDailyDeltas
import com.rpsonline.app.domain.weeklyEloMatchCount
import com.rpsonline.app.domain.weeklyEloNetDelta
import com.rpsonline.app.R
import com.rpsonline.app.ui.components.RpsCard
import com.rpsonline.app.ui.components.formatEloDelta
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val ChartHeight = 148.dp
private val ChartVerticalPadding = 8.dp
private val BarCornerRadius = 4.dp
private val MinBarHeight = 6.dp
private val SideAxisWidth = 28.dp
private val MatchPointRadius = 3.dp
private val MatchLineWidth = 2.dp
private val SkeletonBarHeights = floatArrayOf(0.34f, 0.52f, 0.28f, 0.62f, 0.38f, 0.48f, 0.42f)
private val SkeletonLineHeights = floatArrayOf(0.22f, 0.46f, 0.34f, 0.58f, 0.42f, 0.5f, 0.36f)

@Composable
fun WeeklyEloGainLossChart(
    days: List<DailyEloDelta>,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    sharedMatchesOnly: Boolean = false,
    sharedMatchupLabel: String? = null,
) {
    Crossfade(
        targetState = isLoading,
        modifier = modifier,
        label = "weeklyEloChart",
    ) { loading ->
        if (loading) {
            WeeklyEloChartSkeleton(sharedMatchesOnly = sharedMatchesOnly)
        } else {
            WeeklyEloChartContent(
                days = days,
                sharedMatchesOnly = sharedMatchesOnly,
                sharedMatchupLabel = sharedMatchupLabel,
            )
        }
    }
}

@Composable
private fun WeeklyEloChartContent(
    days: List<DailyEloDelta>,
    sharedMatchesOnly: Boolean,
    sharedMatchupLabel: String?,
) {
    val chartDays = remember(days) {
        if (days.size == 7) days else weeklyEloDailyDeltas(emptyList())
    }
    val netDelta = remember(chartDays) { weeklyEloNetDelta(chartDays) }
    val totalMatchCount = remember(chartDays) { weeklyEloMatchCount(chartDays) }
    val maxDailyMatches = remember(chartDays) { max(chartDays.maxOfOrNull { it.matchCount } ?: 0, 1) }
    val maxEloMagnitude = remember(chartDays) {
        max(chartDays.maxOfOrNull { abs(it.netDelta) } ?: 0, 1)
    }
    val gainColor = MaterialTheme.colorScheme.primary
    val lossColor = MaterialTheme.colorScheme.error
    val matchLineColor = MaterialTheme.colorScheme.tertiary
    val pointInnerColor = MaterialTheme.colorScheme.surface
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val netColor = when {
        netDelta > 0 -> gainColor
        netDelta < 0 -> lossColor
        else -> labelColor
    }

    val summary = remember(chartDays, netDelta, totalMatchCount, sharedMatchesOnly) {
        buildString {
            append("Weekly ELO change ")
            append(formatEloDelta(netDelta))
            append(". ")
            append(totalMatchCount)
            append(if (totalMatchCount == 1) " match" else " matches")
            if (sharedMatchesOnly) {
                append(" you played against in the last 7 days.")
            } else {
                append(" in the last 7 days.")
            }
            chartDays.filter { it.matchCount > 0 || it.netDelta != 0 }.forEach { day ->
                append(' ')
                append(day.dayLabel)
                append(": ")
                append(formatEloDelta(day.netDelta))
                append(", ")
                append(day.matchCount)
                append(if (day.matchCount == 1) " match" else " matches")
                append('.')
            }
        }
    }

    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.this_week),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (sharedMatchesOnly && !sharedMatchupLabel.isNullOrBlank()) {
                        Text(
                            text = sharedMatchupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
                    }
                }
                Text(
                    text = formatEloDelta(netDelta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = netColor,
                )
            }

            ChartLegend(
                matchLineColor = matchLineColor,
                gainColor = gainColor,
                labelColor = labelColor,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MatchCountAxis(
                    maxCount = maxDailyMatches,
                    lineColor = matchLineColor,
                    modifier = Modifier
                        .width(SideAxisWidth)
                        .fillMaxHeight()
                        .padding(vertical = ChartVerticalPadding),
                )
                WeeklyEloComboChart(
                    days = chartDays,
                    maxDailyMatches = maxDailyMatches,
                    maxEloMagnitude = maxEloMagnitude,
                    gainColor = gainColor,
                    lossColor = lossColor,
                    matchLineColor = matchLineColor,
                    pointInnerColor = pointInnerColor,
                    neutralColor = neutralColor,
                    gridColor = gridColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = summary },
                )
                EloDeltaAxis(
                    maxMagnitude = maxEloMagnitude,
                    gainColor = gainColor,
                    lossColor = lossColor,
                    labelColor = labelColor,
                    modifier = Modifier
                        .width(SideAxisWidth)
                        .fillMaxHeight()
                        .padding(vertical = ChartVerticalPadding),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SideAxisWidth, end = SideAxisWidth),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                chartDays.forEach { day ->
                    Text(
                        text = day.dayLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyEloChartSkeleton(
    sharedMatchesOnly: Boolean,
) {
    val loadingChartDescription = stringResource(R.string.loading_weekly_chart)
    val pulseAlpha by rememberInfiniteTransition(label = "weeklyChartSkeleton")
        .animateFloat(
            initialValue = 0.28f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "weeklyChartSkeletonAlpha",
        )
    val skeletonColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    RpsCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingChartDescription },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartSkeletonBar(width = 72.dp, height = 14.dp, alpha = pulseAlpha, color = skeletonColor)
                    if (sharedMatchesOnly) {
                        ChartSkeletonBar(width = 132.dp, height = 10.dp, alpha = pulseAlpha, color = skeletonColor)
                    }
                }
                ChartSkeletonBar(width = 36.dp, height = 14.dp, alpha = pulseAlpha, color = skeletonColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChartSkeletonBar(width = 56.dp, height = 10.dp, alpha = pulseAlpha, color = skeletonColor)
                ChartSkeletonBar(width = 40.dp, height = 10.dp, alpha = pulseAlpha, color = skeletonColor)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(SideAxisWidth)
                        .fillMaxHeight()
                        .padding(vertical = ChartVerticalPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start,
                ) {
                    repeat(3) {
                        ChartSkeletonBar(width = 16.dp, height = 10.dp, alpha = pulseAlpha, color = skeletonColor)
                    }
                }
                WeeklyEloChartSkeletonCanvas(
                    skeletonColor = skeletonColor,
                    gridColor = gridColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = ChartVerticalPadding)
                        .alpha(pulseAlpha),
                )
                Column(
                    modifier = Modifier
                        .width(SideAxisWidth)
                        .fillMaxHeight()
                        .padding(vertical = ChartVerticalPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    repeat(3) {
                        ChartSkeletonBar(width = 20.dp, height = 10.dp, alpha = pulseAlpha, color = skeletonColor)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SideAxisWidth, end = SideAxisWidth),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChartSkeletonBar(
                            width = 22.dp,
                            height = 10.dp,
                            alpha = pulseAlpha,
                            color = skeletonColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyEloChartSkeletonCanvas(
    skeletonColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { BarCornerRadius.toPx() }

    Canvas(modifier = modifier) {
        val chartWidth = size.width
        val chartHeight = size.height
        val centerY = chartHeight / 2f
        val barSlotWidth = chartWidth / SkeletonBarHeights.size
        val barWidth = barSlotWidth * 0.55f
        val halfBarHeight = chartHeight / 2f - 2f
        val plotTop = 2f
        val plotBottom = chartHeight - 2f
        val plotHeight = plotBottom - plotTop

        drawLine(
            color = gridColor,
            start = Offset(0f, centerY),
            end = Offset(chartWidth, centerY),
            strokeWidth = 1f,
        )

        SkeletonBarHeights.forEachIndexed { index, fraction ->
            val slotCenterX = barSlotWidth * index + barSlotWidth / 2f
            val left = slotCenterX - barWidth / 2f
            val barHeight = halfBarHeight * fraction
            val top = if (index % 2 == 0) centerY - barHeight else centerY
            drawRoundRect(
                color = skeletonColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
        }

        val linePoints = SkeletonLineHeights.mapIndexed { index, fraction ->
            val x = barSlotWidth * index + barSlotWidth / 2f
            val y = plotBottom - fraction * plotHeight
            Offset(x, y)
        }
        if (linePoints.size >= 2) {
            val path = Path().apply {
                moveTo(linePoints.first().x, linePoints.first().y)
                linePoints.drop(1).forEach { point -> lineTo(point.x, point.y) }
            }
            drawPath(
                path = path,
                color = skeletonColor,
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )
        }
        linePoints.forEach { point ->
            drawCircle(color = skeletonColor, radius = 3f, center = point)
        }
    }
}

@Composable
private fun ChartSkeletonBar(
    width: Dp,
    height: Dp,
    alpha: Float,
    color: Color,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(alpha),
        shape = RoundedCornerShape(4.dp),
        color = color,
    ) {}
}

@Composable
private fun ChartLegend(
    matchLineColor: Color,
    gainColor: Color,
    labelColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                modifier = Modifier
                    .size(width = 14.dp, height = 8.dp),
            ) {
                drawLine(
                    color = matchLineColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = matchLineColor,
                    radius = 2.5f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
            Text(
                text = stringResource(R.string.matches),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.elo_label),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 8.dp)
                    .padding(top = 1.dp),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = gainColor,
                        topLeft = Offset(size.width * 0.1f, 0f),
                        size = Size(size.width * 0.8f, size.height),
                        cornerRadius = CornerRadius(2f, 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCountAxis(
    maxCount: Int,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val midCount = (maxCount / 2f).roundToInt().coerceAtLeast(0)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = maxCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = lineColor,
            textAlign = TextAlign.Start,
        )
        if (midCount in 1 until maxCount) {
            Text(
                text = midCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = lineColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Start,
            )
        }
        Text(
            text = "0",
            style = MaterialTheme.typography.labelSmall,
            color = lineColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun EloDeltaAxis(
    maxMagnitude: Int,
    gainColor: Color,
    lossColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = formatEloDelta(maxMagnitude),
            style = MaterialTheme.typography.labelSmall,
            color = gainColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            text = "0",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            textAlign = TextAlign.End,
        )
        Text(
            text = formatEloDelta(-maxMagnitude),
            style = MaterialTheme.typography.labelSmall,
            color = lossColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeeklyEloComboChart(
    days: List<DailyEloDelta>,
    maxDailyMatches: Int,
    maxEloMagnitude: Int,
    gainColor: Color,
    lossColor: Color,
    matchLineColor: Color,
    pointInnerColor: Color,
    neutralColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { BarCornerRadius.toPx() }
    val minBarHeightPx = with(density) { MinBarHeight.toPx() }
    val pointRadiusPx = with(density) { MatchPointRadius.toPx() }
    val lineWidthPx = with(density) { MatchLineWidth.toPx() }
    val maxMagnitude = max(maxEloMagnitude, 1)

    Canvas(modifier = modifier.padding(vertical = ChartVerticalPadding)) {
        val chartWidth = size.width
        val chartHeight = size.height
        val centerY = chartHeight / 2f
        val barSlotWidth = chartWidth / max(days.size, 1)
        val barWidth = barSlotWidth * 0.55f
        val halfBarHeight = chartHeight / 2f - 2f
        val plotTop = 2f
        val plotBottom = chartHeight - 2f
        val plotHeight = plotBottom - plotTop

        drawLine(
            color = gridColor,
            start = Offset(0f, centerY),
            end = Offset(chartWidth, centerY),
            strokeWidth = 1f,
        )

        days.forEachIndexed { index, day ->
            val slotCenterX = barSlotWidth * index + barSlotWidth / 2f
            val left = slotCenterX - barWidth / 2f

            if (day.netDelta == 0) {
                drawRoundRect(
                    color = neutralColor,
                    topLeft = Offset(left, centerY - 1.5f),
                    size = Size(barWidth, 3f),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            } else {
                val scaledHeight = halfBarHeight * (abs(day.netDelta).toFloat() / maxMagnitude)
                val barHeight = max(scaledHeight, minBarHeightPx)
                val color = if (day.netDelta > 0) gainColor else lossColor
                if (day.netDelta > 0) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, centerY - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, centerY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                }
            }
        }

        val matchPoints = days.mapIndexed { index, day ->
            val slotCenterX = barSlotWidth * index + barSlotWidth / 2f
            val normalized = day.matchCount.toFloat() / maxDailyMatches
            val y = plotBottom - normalized * plotHeight
            Offset(slotCenterX, y)
        }

        if (matchPoints.size >= 2) {
            val path = Path().apply {
                moveTo(matchPoints.first().x, matchPoints.first().y)
                matchPoints.drop(1).forEach { point ->
                    lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = path,
                color = matchLineColor,
                style = Stroke(width = lineWidthPx, cap = StrokeCap.Round),
            )
        }

        matchPoints.forEach { point ->
            drawCircle(
                color = matchLineColor,
                radius = pointRadiusPx,
                center = point,
            )
            drawCircle(
                color = pointInnerColor,
                radius = pointRadiusPx * 0.45f,
                center = point,
            )
        }
    }
}
