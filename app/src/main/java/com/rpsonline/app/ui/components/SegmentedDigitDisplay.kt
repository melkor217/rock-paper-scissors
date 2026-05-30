package com.rpsonline.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.ui.theme.isRpsDarkTheme
import com.rpsonline.app.ui.util.LocalSegmentedDisplayPulseMove
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Current half-lit pulse blend for the full top-bar segmented row (0 = normal). */
val LocalSegmentedDisplayPulseAlpha = compositionLocalOf { 0f }

/** Fill progress for resolution bursts (0 → 1 during rise/hold; drives segment sequence). */
val LocalSegmentedDisplayPulseFill = compositionLocalOf { 0f }

private const val ResolutionPulseDurationMs = 320
/** Fill sequence runs over this span (~75% of burst); each step gets an equal hold. */
private const val ResolutionPulseFillCompleteAtMs = 240
private const val ResolutionPulseHoldUntilMs = 275

private fun resolutionPulseFillAnimationSpec(move: Move) = keyframes {
    durationMillis = ResolutionPulseDurationMs
    val stepCount = resolutionBurstFillSequence(move).size
    if (stepCount <= 1) {
        0f at 0
        1f at ResolutionPulseFillCompleteAtMs
        1f at ResolutionPulseDurationMs
        return@keyframes
    }
    for (index in 0 until stepCount) {
        val progress = index.toFloat() / (stepCount - 1)
        val stepStartMs = (ResolutionPulseFillCompleteAtMs * index / (stepCount - 1)).toInt()
        val stepEndMs = if (index < stepCount - 1) {
            (ResolutionPulseFillCompleteAtMs * (index + 1) / (stepCount - 1)).toInt() - 1
        } else {
            ResolutionPulseDurationMs
        }
        progress at stepStartMs
        if (index < stepCount - 1) {
            progress at stepEndMs.coerceAtLeast(stepStartMs)
        }
    }
    1f at ResolutionPulseDurationMs
}

private val allSevenSegments = setOf('a', 'b', 'c', 'd', 'e', 'f', 'g')

private fun cumulativeFillSteps(additions: List<Set<Char>>): List<Set<Char>> {
    var accumulated = emptySet<Char>()
    return additions.map { step ->
        accumulated = accumulated + step
        accumulated
    }
}

/** Move-specific fill steps; each step adds segments until all are lit at peak. */
fun resolutionBurstFillSequence(move: Move): List<Set<Char>> = when (move) {
    Move.ROCK -> cumulativeFillSteps(
        listOf(
            setOf('g'),
            setOf('a', 'd'),
            setOf('f', 'b'),
            setOf('e', 'c'),
        ),
    )
    Move.PAPER -> cumulativeFillSteps(
        listOf(
            setOf('a'),
            setOf('f', 'b'),
            setOf('g'),
            setOf('d'),
            setOf('e', 'c'),
        ),
    )
    Move.SCISSORS -> cumulativeFillSteps(
        listOf(
            setOf('f', 'b'),
            setOf('e', 'c'),
            setOf('g'),
            setOf('a', 'd'),
        ),
    )
}

/** Segments lit at burst fill progress in [0, 1]; peak uses all segments. */
fun resolutionBurstSegmentsAtProgress(move: Move, fillProgress: Float): Set<Char> {
    if (fillProgress >= 1f) return allSevenSegments
    if (fillProgress <= 0f) return emptySet()
    val sequence = resolutionBurstFillSequence(move)
    val fillCompleteProgress = ResolutionPulseFillCompleteAtMs.toFloat() / ResolutionPulseDurationMs
    if (fillProgress >= fillCompleteProgress) {
        return sequence.last()
    }
    val index = (fillProgress * (sequence.size - 1))
        .toInt()
        .coerceIn(0, sequence.lastIndex)
    return sequence[index]
}

/** Burst segments that may be half-lit without touching protected full-lit segments. */
fun resolutionBurstSegmentsExcluding(
    move: Move,
    progress: Float,
    protectedSegments: Set<Char>,
): Set<Char> = resolutionBurstSegmentsAtProgress(move, progress) - protectedSegments

/** Final move-specific shape one step before the all-segment peak. */
fun resolutionBurstSegments(move: Move): Set<Char> =
    resolutionBurstFillSequence(move).let { sequence ->
        if (sequence.size < 2) sequence.lastOrNull().orEmpty() else sequence[sequence.lastIndex - 1]
    }

/** Drives resolution pulse alpha for all segmented digits in [content]. */
@Composable
fun SegmentedDisplayPulseEffect(
    resolutionPulseTrigger: Int,
    pulseMove: Move,
    content: @Composable () -> Unit,
) {
    var pulseAlphaValue by remember { mutableFloatStateOf(0f) }
    var pulseFillProgress by remember { mutableFloatStateOf(0f) }
    var lastPulseTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(resolutionPulseTrigger) {
        if (resolutionPulseTrigger <= lastPulseTrigger) return@LaunchedEffect
        while (lastPulseTrigger < resolutionPulseTrigger) {
            lastPulseTrigger++
            try {
                pulseAlphaValue = 0f
                pulseFillProgress = 0f
                coroutineScope {
                    val fillJob = launch {
                        animate(
                            initialValue = 0f,
                            targetValue = 0f,
                            animationSpec = resolutionPulseFillAnimationSpec(pulseMove),
                        ) { value, _ ->
                            pulseFillProgress = value
                        }
                    }
                    animate(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = ResolutionPulseDurationMs
                            0f at 0
                            1f at ResolutionPulseFillCompleteAtMs using FastOutSlowInEasing
                            1f at ResolutionPulseHoldUntilMs
                            0f at ResolutionPulseDurationMs using LinearOutSlowInEasing
                        },
                    ) { value, _ ->
                        pulseAlphaValue = value
                    }
                    fillJob.join()
                }
            } finally {
                pulseAlphaValue = 0f
                pulseFillProgress = 0f
            }
        }
    }

    CompositionLocalProvider(
        LocalSegmentedDisplayPulseAlpha provides pulseAlphaValue,
        LocalSegmentedDisplayPulseFill provides pulseFillProgress,
        LocalSegmentedDisplayPulseMove provides pulseMove,
    ) {
        content()
    }
}

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
    val pulseAlpha = LocalSegmentedDisplayPulseAlpha.current
    SegmentedDisplayPulseSlot(
        pulseAlpha = pulseAlpha,
        offColor = ghost,
        digitWidth = digitWidth,
        digitHeight = digitHeight,
        modifier = modifier,
    )
}

@Composable
private fun SevenSegmentValuePulseSlot(
    digit: Char,
    isLeadingZero: Boolean,
    pulseAlpha: Float,
    offColor: Color,
    digitWidth: Dp,
    digitHeight: Dp,
) {
    val segments = segmentsFor(digit)
    SegmentedDisplayPulseSlot(
        pulseAlpha = pulseAlpha,
        offColor = offColor,
        fullLitSegments = if (isLeadingZero) emptySet() else segments,
        halfLitSegments = if (isLeadingZero) segments else emptySet(),
        digitWidth = digitWidth,
        digitHeight = digitHeight,
    )
}

@Composable
fun ThreeDigitSegmentedDisplay(
    value: Int?,
    modifier: Modifier = Modifier,
    digitWidth: Dp = SegmentedDigitWidth,
    digitHeight: Dp = SegmentedDigitHeight,
) {
    val offColor = sevenSegmentGhostColor()
    val pulseAlpha = LocalSegmentedDisplayPulseAlpha.current

    if (value == null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                SegmentedDisplayPulseSlot(
                    pulseAlpha = pulseAlpha,
                    offColor = offColor,
                    digitWidth = digitWidth,
                    digitHeight = digitHeight,
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
            SevenSegmentValuePulseSlot(
                digit = char,
                isLeadingZero = index < firstSignificantIndex,
                pulseAlpha = pulseAlpha,
                offColor = offColor,
                digitWidth = digitWidth,
                digitHeight = digitHeight,
            )
        }
    }
}

enum class SegmentedSpinnerStyle {
    QUEUE,
    MATCH,
    MATCH_CLOCK_STOPPED,
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
    val pulseAlpha = LocalSegmentedDisplayPulseAlpha.current
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
        SpinningSevenSegmentPulseSlot(
            pulseAlpha = pulseAlpha,
            offColor = offColor,
            digitWidth = digitWidth,
            digitHeight = digitHeight,
            animate = animateSpinner,
            style = spinnerStyle,
        )
        SpacerBetweenSegments()
        SegmentedDisplayPulseSlot(
            pulseAlpha = pulseAlpha,
            offColor = offColor,
            digitWidth = digitWidth,
            digitHeight = digitHeight,
        )
        SpacerBetweenSegments()
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.take(2).forEachIndexed { index, digit ->
                if (showLiveTime) {
                    SevenSegmentValuePulseSlot(
                        digit = digit,
                        isLeadingZero = index < firstSignificantIndex,
                        pulseAlpha = pulseAlpha,
                        offColor = offColor,
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                } else {
                    SegmentedDisplayPulseSlot(
                        pulseAlpha = pulseAlpha,
                        offColor = offColor,
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                }
            }
        }
        SegmentedColonPulseSlot(
            pulseAlpha = pulseAlpha,
            lit = showLiveTime,
            litColor = litColor,
            offColor = offColor,
            digitHeight = digitHeight,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegmentedDigitSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.drop(2).forEachIndexed { index, digit ->
                val digitIndex = index + 2
                if (showLiveTime) {
                    SevenSegmentValuePulseSlot(
                        digit = digit,
                        isLeadingZero = digitIndex < firstSignificantIndex,
                        pulseAlpha = pulseAlpha,
                        offColor = offColor,
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                } else {
                    SegmentedDisplayPulseSlot(
                        pulseAlpha = pulseAlpha,
                        offColor = offColor,
                        digitWidth = digitWidth,
                        digitHeight = digitHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedDisplayPulseSlot(
    pulseAlpha: Float,
    offColor: Color,
    digitWidth: Dp,
    digitHeight: Dp,
    modifier: Modifier = Modifier,
    fullLitSegments: Set<Char> = emptySet(),
    halfLitSegments: Set<Char> = emptySet(),
) {
    val pulseMove = LocalSegmentedDisplayPulseMove.current
    val fillProgress = LocalSegmentedDisplayPulseFill.current.coerceIn(0f, 1f)
    val brightness = pulseAlpha.coerceIn(0f, 1f)
    val protectedSegments = fullLitSegments + halfLitSegments
    val burstSegments = if (fillProgress > 0.001f && brightness > 0.001f) {
        resolutionBurstSegmentsExcluding(pulseMove, fillProgress, protectedSegments)
    } else {
        emptySet()
    }

    SevenSegmentDisplayWithPulse(
        fullLitSegments = fullLitSegments,
        halfLitSegments = halfLitSegments,
        burstSegments = burstSegments,
        burstAlpha = brightness,
        offColor = offColor,
        modifier = modifier.size(digitWidth, digitHeight),
    )
}

@Composable
private fun SegmentedColonPulseSlot(
    pulseAlpha: Float,
    lit: Boolean,
    litColor: Color,
    offColor: Color,
    digitHeight: Dp,
    modifier: Modifier = Modifier,
) {
    SevenSegmentTimeColon(
        color = when {
            lit -> litColor
            pulseAlpha > 0.001f -> sevenSegmentHalfLitColor()
            else -> offColor
        },
        digitHeight = digitHeight,
        lit = lit,
        modifier = modifier.padding(horizontal = SegmentedTimeColonSpacing),
    )
}

@Composable
private fun SevenSegmentDisplayWithPulse(
    fullLitSegments: Set<Char>,
    halfLitSegments: Set<Char>,
    burstSegments: Set<Char>,
    burstAlpha: Float,
    offColor: Color,
    modifier: Modifier = Modifier,
) {
    val fullLitColor = sevenSegmentLitColor()
    val halfLitColor = sevenSegmentHalfLitColor()
    Canvas(modifier = modifier) {
        val layout = sevenSegmentLayout(size.width, size.height)
        layout
            .sortedWith(
                compareBy<SegmentLayout> { segment ->
                    when {
                        segment.id in fullLitSegments -> 3
                        segment.id in halfLitSegments -> 2
                        segment.id in burstSegments -> 1
                        else -> 0
                    }
                }.thenBy { if (it.id == 'g') 1 else 0 },
            )
            .forEach { segment ->
                when {
                    segment.id in fullLitSegments -> drawSevenSegmentLit(segment, fullLitColor)
                    segment.id in halfLitSegments -> drawSevenSegment(segment, halfLitColor)
                    segment.id in burstSegments && burstAlpha > 0.001f -> {
                        drawSevenSegment(
                            segment,
                            lerp(offColor, halfLitColor, burstAlpha),
                        )
                    }
                    else -> drawSevenSegment(segment, offColor)
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

/** Slow pause bars and side pairs — player submitted; match clock held. */
private val matchClockStoppedSpinnerSteps = listOf(
    setOf('a', 'd'),
    setOf('a', 'd'),
    setOf('g'),
    setOf('g'),
    setOf('f', 'e'),
    setOf('b', 'c'),
    setOf('f', 'e'),
    setOf('b', 'c'),
)

@Composable
private fun SpinningSevenSegmentPulseSlot(
    pulseAlpha: Float,
    offColor: Color,
    digitWidth: Dp,
    digitHeight: Dp,
    animate: Boolean = true,
    style: SegmentedSpinnerStyle = SegmentedSpinnerStyle.QUEUE,
    modifier: Modifier = Modifier,
) {
    val steps = when (style) {
        SegmentedSpinnerStyle.QUEUE -> queueSpinnerSegmentSteps
        SegmentedSpinnerStyle.MATCH -> matchSpinnerSegmentSteps
        SegmentedSpinnerStyle.MATCH_CLOCK_STOPPED -> matchClockStoppedSpinnerSteps
    }
    val stepDelayMs = when (style) {
        SegmentedSpinnerStyle.QUEUE -> 90L
        SegmentedSpinnerStyle.MATCH -> 180L
        SegmentedSpinnerStyle.MATCH_CLOCK_STOPPED -> 340L
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
    SegmentedDisplayPulseSlot(
        pulseAlpha = pulseAlpha,
        offColor = offColor,
        halfLitSegments = if (animate) steps[step] else emptySet(),
        digitWidth = digitWidth,
        digitHeight = digitHeight,
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
