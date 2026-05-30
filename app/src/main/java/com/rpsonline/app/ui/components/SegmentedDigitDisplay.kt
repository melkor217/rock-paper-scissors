package com.rpsonline.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpsonline.app.ui.theme.isRpsDarkTheme
import kotlinx.coroutines.delay

/** Default seven-segment digit size for top-bar indicators. */
val SegmentedDigitWidth = 11.dp
val SegmentedDigitHeight = 18.dp
val SegmentedDisplayHeight = 36.dp
private val SegmentedDigitSpacing = 1.dp
/** Tighter gap between MM, colon, and SS on the queue timer. */
private val SegmentedTimeColonSpacing = 0.dp

@Composable
private fun sevenSegmentGhostColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isRpsDarkTheme()) {
        lerp(
            lerp(scheme.surfaceContainerLow, scheme.surface, 0.38f),
            scheme.outlineVariant,
            0.06f,
        )
    } else {
        lerp(scheme.surfaceContainerHigh, scheme.outlineVariant, 0.36f)
            .copy(alpha = 0.87f)
    }
}

@Composable
private fun sevenSegmentLitColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isRpsDarkTheme()) {
        lerp(scheme.primary, scheme.onPrimaryContainer, 0.48f)
    } else {
        lerp(scheme.primary, scheme.onPrimaryContainer, 0.12f)
    }
}

@Composable
private fun sevenSegmentHalfLitColor(): Color {
    return lerp(sevenSegmentGhostColor(), sevenSegmentLitColor(), 0.58f)
}
@Composable
fun SevenSegmentBlankSlot(
    modifier: Modifier = Modifier,
    digitWidth: Dp = SegmentedDigitWidth,
    digitHeight: Dp = SegmentedDigitHeight,
) {
    val ghost = sevenSegmentGhostColor()
    SevenSegmentDigit(
        digit = '8',
        segmentOnColor = ghost,
        segmentOffColor = ghost,
        showGhostOnly = true,
        modifier = modifier.size(digitWidth, digitHeight),
    )
}

@Composable
private fun SevenSegmentValueDigit(
    digit: Char,
    isLeadingZero: Boolean,
    litColor: Color,
    offColor: Color,
    modifier: Modifier = Modifier,
) {
    SevenSegmentDigit(
        digit = digit,
        segmentOnColor = if (isLeadingZero) sevenSegmentHalfLitColor() else litColor,
        segmentOffColor = offColor,
        modifier = modifier,
    )
}

@Composable
fun ThreeDigitSegmentedDisplay(
    value: Int?,
    modifier: Modifier = Modifier,
    digitWidth: Dp = SegmentedDigitWidth,
    digitHeight: Dp = SegmentedDigitHeight,
) {
    val litColor = sevenSegmentLitColor()
    val offColor = sevenSegmentGhostColor()

    if (value == null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                SevenSegmentDigit(
                    digit = '8',
                    segmentOnColor = offColor,
                    segmentOffColor = offColor,
                    showGhostOnly = true,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }
        }
        return
    }

    val clamped = value.coerceIn(0, 999)
    val digits = clamped.toString().padStart(3, '0')
    val firstSignificantIndex = digits.indexOfFirst { it != '0' }.let { index ->
        if (index < 0) 2 else index
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        digits.forEachIndexed { index, char ->
            SevenSegmentValueDigit(
                digit = char,
                isLeadingZero = index < firstSignificantIndex,
                litColor = litColor,
                offColor = offColor,
                modifier = Modifier.size(digitWidth, digitHeight),
            )
        }
    }
}

enum class SegmentedSpinnerStyle {
    QUEUE,
    MATCH,
}

/** Spinner segment + four digits for queue elapsed time (MM:SS). */
@Composable
fun QueueTimeSegmentedDisplay(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
    digitWidth: Dp = SegmentedDigitWidth,
    digitHeight: Dp = SegmentedDigitHeight,
    litColor: Color = sevenSegmentLitColor(),
    offColor: Color = sevenSegmentGhostColor(),
    showLiveTime: Boolean = true,
    animateSpinner: Boolean = true,
    spinnerStyle: SegmentedSpinnerStyle = SegmentedSpinnerStyle.QUEUE,
) {
    val totalSeconds = elapsedSeconds.coerceAtLeast(0)
    val minutes = (totalSeconds / 60).coerceAtMost(99)
    val seconds = (totalSeconds % 60).coerceAtMost(59)
    val digits = "%02d%02d".format(minutes, seconds).toList()
    val firstSignificantIndex = digits.indexOfFirst { it != '0' }.let { index ->
        if (index < 0) digits.lastIndex else index
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpinningSevenSegmentSlot(
            segmentOffColor = offColor,
            animate = animateSpinner,
            style = spinnerStyle,
            modifier = Modifier.size(digitWidth, digitHeight),
        )
        SpacerBetweenSegments()
        SevenSegmentBlankSlot(digitWidth = digitWidth, digitHeight = digitHeight)
        SpacerBetweenSegments()
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.take(2).forEachIndexed { index, digit ->
                if (showLiveTime) {
                    SevenSegmentValueDigit(
                        digit = digit,
                        isLeadingZero = index < firstSignificantIndex,
                        litColor = litColor,
                        offColor = offColor,
                        modifier = Modifier.size(digitWidth, digitHeight),
                    )
                } else {
                    SevenSegmentBlankSlot(
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                }
            }
        }
        SevenSegmentTimeColon(
            color = if (showLiveTime) litColor else offColor,
            digitHeight = digitHeight,
            lit = showLiveTime,
            modifier = Modifier.padding(horizontal = SegmentedTimeColonSpacing),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.drop(2).forEachIndexed { index, digit ->
                val digitIndex = index + 2
                if (showLiveTime) {
                    SevenSegmentValueDigit(
                        digit = digit,
                        isLeadingZero = digitIndex < firstSignificantIndex,
                        litColor = litColor,
                        offColor = offColor,
                        modifier = Modifier.size(digitWidth, digitHeight),
                    )
                } else {
                    SevenSegmentBlankSlot(
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpacerBetweenSegments() {
    Spacer(modifier = Modifier.width(SegmentedDigitSpacing))
}

/** Small fixed colon between MM and SS (not a seven-segment digit). */
@Composable
private fun SevenSegmentTimeColon(
    color: Color,
    digitHeight: Dp,
    lit: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(width = 5.dp, height = digitHeight),
    ) {
        val centerX = size.width / 2f
        drawColonPip(Offset(centerX, size.height * 0.36f), color, lit)
        drawColonPip(Offset(centerX, size.height * 0.64f), color, lit)
    }
}

private fun DrawScope.drawColonPip(center: Offset, color: Color, lit: Boolean) {
    val width = size.width * 0.7f
    val height = width * 0.52f
    if (!lit) {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f, height / 2f),
        )
        return
    }
    val glowLayers = listOf(
        Triple(0.42f, 0.18f, 1.02f),
        Triple(0.24f, 0.32f, 1.0f),
        Triple(0.1f, 0.5f, 1.0f),
    )
    glowLayers.forEach { (inflate, alpha, scale) ->
        val glowW = width + height * inflate * 2f
        val glowH = height * scale + height * inflate * 0.45f
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(center.x - glowW / 2f, center.y - glowH / 2f),
            size = Size(glowW, glowH),
            cornerRadius = CornerRadius(glowH / 2f, glowH / 2f),
        )
    }
    val coreH = height * 1.12f
    val coreW = width * 1.04f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - coreW / 2f, center.y - coreH / 2f),
        size = Size(coreW, coreH),
        cornerRadius = CornerRadius(coreH / 2f, coreH / 2f),
    )
}

private val queueSpinnerSegmentSteps = listOf(
    setOf('f'),
    setOf('a'),
    setOf('b'),
    setOf('g'),
    setOf('c'),
    setOf('d'),
    setOf('e'),
)

/** Pairs and bars — visually distinct from the queue single-segment chase. */
private val matchSpinnerSegmentSteps = listOf(
    setOf('a', 'd'),
    setOf('f', 'b'),
    setOf('g'),
    setOf('e', 'c'),
    setOf('a', 'g'),
    setOf('d', 'g'),
    setOf('f', 'e'),
    setOf('b', 'c'),
)

@Composable
private fun SpinningSevenSegmentSlot(
    segmentOffColor: Color,
    animate: Boolean = true,
    style: SegmentedSpinnerStyle = SegmentedSpinnerStyle.QUEUE,
    modifier: Modifier = Modifier,
) {
    val segmentOnColor = sevenSegmentHalfLitColor()
    val steps = when (style) {
        SegmentedSpinnerStyle.QUEUE -> queueSpinnerSegmentSteps
        SegmentedSpinnerStyle.MATCH -> matchSpinnerSegmentSteps
    }
    val stepDelayMs = when (style) {
        SegmentedSpinnerStyle.QUEUE -> 90L
        SegmentedSpinnerStyle.MATCH -> 180L
    }
    var step by remember(style) { mutableIntStateOf(0) }
    LaunchedEffect(animate, style) {
        if (!animate) {
            step = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(stepDelayMs)
            step = (step + 1) % steps.size
        }
    }
    SevenSegmentDisplay(
        activeSegments = if (animate) steps[step] else emptySet(),
        segmentOnColor = segmentOnColor,
        segmentOffColor = segmentOffColor,
        modifier = modifier,
    )
}

@Composable
private fun SevenSegmentDigit(
    digit: Char,
    segmentOnColor: Color,
    segmentOffColor: Color,
    modifier: Modifier = Modifier,
    showGhostOnly: Boolean = false,
) {
    SevenSegmentDisplay(
        activeSegments = if (showGhostOnly) emptySet() else segmentsFor(digit),
        segmentOnColor = segmentOnColor,
        segmentOffColor = segmentOffColor,
        modifier = modifier,
    )
}

private fun sevenSegmentLayout(width: Float, height: Float): List<SegmentLayout> {
    val thickness = (width * 0.15f).coerceAtLeast(1f)
    val gap = thickness * 0.42f
    val sideInset = width * 0.05f

    val leftX = sideInset
    val rightX = width - sideInset - thickness
    val barLeft = leftX + thickness + gap
    val barWidth = (rightX - gap - barLeft).coerceAtLeast(thickness)

    val topY = height * 0.05f
    val bottomY = height * 0.95f - thickness
    val midY = height * 0.5f - thickness / 2f

    val upperVertTop = topY + thickness + gap
    val upperVertBottom = midY - gap
    val upperVertLen = (upperVertBottom - upperVertTop).coerceAtLeast(thickness)

    val lowerVertTop = midY + thickness + gap
    val lowerVertBottom = bottomY - gap
    val lowerVertLen = (lowerVertBottom - lowerVertTop).coerceAtLeast(thickness)

    return listOf(
        SegmentLayout('a', barLeft, topY, barWidth, thickness, horizontal = true),
        SegmentLayout('g', barLeft, midY, barWidth, thickness, horizontal = true),
        SegmentLayout('d', barLeft, bottomY, barWidth, thickness, horizontal = true),
        SegmentLayout('f', leftX, upperVertTop, upperVertLen, thickness, horizontal = false),
        SegmentLayout('b', rightX, upperVertTop, upperVertLen, thickness, horizontal = false),
        SegmentLayout('e', leftX, lowerVertTop, lowerVertLen, thickness, horizontal = false),
        SegmentLayout('c', rightX, lowerVertTop, lowerVertLen, thickness, horizontal = false),
    )
}

@Composable
private fun SevenSegmentDisplay(
    activeSegments: Set<Char>,
    segmentOnColor: Color,
    segmentOffColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val layout = sevenSegmentLayout(size.width, size.height)
        val ghostColor = segmentOffColor
        val litColor = segmentOnColor.copy(alpha = 1f)
        layout
            .sortedWith(
                compareBy<SegmentLayout> { if (it.id in activeSegments) 1 else 0 }
                    .thenBy { if (it.id == 'g') 1 else 0 },
            )
            .forEach { segment ->
                if (segment.id in activeSegments) {
                    drawSevenSegmentLit(segment, litColor)
                } else {
                    drawSevenSegment(segment, ghostColor)
                }
            }
    }
}

private data class SegmentLayout(
    val id: Char,
    val left: Float,
    val top: Float,
    val length: Float,
    val thickness: Float,
    val horizontal: Boolean,
)

private fun DrawScope.drawSevenSegment(
    segment: SegmentLayout,
    color: Color,
    inflate: Float = 0f,
    thicknessScale: Float = 1f,
) {
    val thickness = segment.thickness * thicknessScale
    val corner = CornerRadius(thickness * 0.22f, thickness * 0.22f)
    if (segment.horizontal) {
        val width = segment.length + inflate * 2f
        val height = thickness + inflate * 0.5f
        drawRoundRect(
            color = color,
            topLeft = Offset(segment.left - inflate, segment.top - (height - segment.thickness) / 2f),
            size = Size(width, height),
            cornerRadius = corner,
        )
    } else {
        val width = thickness + inflate * 0.5f
        val height = segment.length + inflate * 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(
                segment.left - (width - segment.thickness) / 2f,
                segment.top - inflate,
            ),
            size = Size(width, height),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawSevenSegmentLit(segment: SegmentLayout, color: Color) {
    val base = segment.thickness
    drawSevenSegment(segment, color.copy(alpha = 0.2f), inflate = base * 0.72f)
    drawSevenSegment(segment, color.copy(alpha = 0.34f), inflate = base * 0.46f)
    drawSevenSegment(segment, color.copy(alpha = 0.52f), inflate = base * 0.24f)
    drawSevenSegment(
        segment,
        color,
        inflate = base * 0.06f,
        thicknessScale = 1.16f,
    )
}

private fun segmentsFor(digit: Char): Set<Char> = when (digit) {
    '0' -> setOf('a', 'b', 'c', 'd', 'e', 'f')
    '1' -> setOf('b', 'c')
    '2' -> setOf('a', 'b', 'd', 'e', 'g')
    '3' -> setOf('a', 'b', 'c', 'd', 'g')
    '4' -> setOf('b', 'c', 'f', 'g')
    '5' -> setOf('a', 'c', 'd', 'f', 'g')
    '6' -> setOf('a', 'c', 'd', 'e', 'f', 'g')
    '7' -> setOf('a', 'b', 'c')
    '8' -> setOf('a', 'b', 'c', 'd', 'e', 'f', 'g')
    '9' -> setOf('a', 'b', 'c', 'd', 'f', 'g')
    else -> emptySet()
}
