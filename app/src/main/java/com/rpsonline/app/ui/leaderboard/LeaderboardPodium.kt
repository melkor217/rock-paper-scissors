package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.max

private data class PodiumStyle(
    val borderColor: Color,
    val glowColor: Color,
    val containerTint: Color,
    val rankLabelColor: Color,
)

private fun podiumStyleForRank(rank: Int, dark: Boolean): PodiumStyle? = when (rank) {
        1 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFFFD54F).copy(alpha = 0.45f) else Color(0xFFC9A227).copy(alpha = 0.35f),
            glowColor = if (dark) Color(0xFFFFCA28) else Color(0xFFC9A227),
            containerTint = if (dark) Color(0xFF5D4037) else Color(0xFFF5F0E6),
            rankLabelColor = if (dark) Color(0xFFFFE082).copy(alpha = 0.85f) else Color(0xFF8A6F1F),
        )
        2 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFE0E0E0).copy(alpha = 0.4f) else Color(0xFF90A4AE).copy(alpha = 0.32f),
            glowColor = if (dark) Color(0xFFB0BEC5) else Color(0xFF90A4AE),
            containerTint = if (dark) Color(0xFF37474F) else Color(0xFFEEF1F2),
            rankLabelColor = if (dark) Color(0xFFECEFF1).copy(alpha = 0.9f) else Color(0xFF5C6B73),
        )
        3 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFFFAB91).copy(alpha = 0.45f) else Color(0xFF9A7B6F).copy(alpha = 0.35f),
            glowColor = if (dark) Color(0xFFFF8A65) else Color(0xFF9A7B6F),
            containerTint = if (dark) Color(0xFF4E342E) else Color(0xFFF3EEEB),
            rankLabelColor = if (dark) Color(0xFFFFCCBC).copy(alpha = 0.85f) else Color(0xFF6B5348),
        )
        else -> null
    }

@Composable
private fun podiumStyleForRank(rank: Int): PodiumStyle? =
    podiumStyleForRank(rank, isSystemInDarkTheme())

private fun DrawScope.drawInwardPodiumGlow(
    glowColor: Color,
    cornerRadius: Float,
    edgeStrength: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = max(size.width, size.height) * 0.72f
    val corner = CornerRadius(cornerRadius, cornerRadius)
    val edgeAlpha = glowColor.copy(alpha = edgeStrength)

    drawRoundRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.5f to glowColor.copy(alpha = 0.04f),
                1.0f to edgeAlpha,
            ),
            center = center,
            radius = radius,
        ),
        size = size,
        cornerRadius = corner,
    )
}

@Composable
fun LeaderboardEntryCard(
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentUser: Boolean = false,
    content: @Composable () -> Unit,
) {
    val podium = podiumStyleForRank(rank)
    val shape = MaterialTheme.shapes.medium
    val baseContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    var containerColor = podium?.let { lerp(baseContainer, it.containerTint, 0.10f) } ?: baseContainer
    if (isCurrentUser) {
        containerColor = lerp(containerColor, MaterialTheme.colorScheme.primaryContainer, 0.48f)
    }
    val border = when {
        isCurrentUser -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        podium != null -> BorderStroke(1.dp, podium.borderColor)
        else -> null
    }
    val cornerRadius = 12.dp
    val glowEdgeStrength = if (isSystemInDarkTheme()) 0.12f else 0.07f

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (podium != null && !isCurrentUser) {
                        Modifier.drawBehind {
                            drawInwardPodiumGlow(
                                glowColor = podium.glowColor,
                                cornerRadius = cornerRadius.toPx(),
                                edgeStrength = glowEdgeStrength,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}

fun leaderboardRankLabelColor(rank: Int, darkTheme: Boolean): Color =
    podiumStyleForRank(rank, darkTheme)?.rankLabelColor ?: Color.Unspecified

@Composable
fun leaderboardRankLabelColor(rank: Int): Color {
    val fallback = MaterialTheme.colorScheme.onSurface
    val color = leaderboardRankLabelColor(rank, isSystemInDarkTheme())
    return if (color == Color.Unspecified) fallback else color
}
