package com.rpsonline.app.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import com.rpsonline.app.ui.theme.isRpsDarkTheme
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
            borderColor = if (dark) Color(0xFFFFD319).copy(alpha = 0.55f) else Color(0xFFC9A000).copy(alpha = 0.45f),
            glowColor = if (dark) Color(0xFFFFD319) else Color(0xFFE6B800),
            containerTint = if (dark) Color(0xFF2A2400) else Color(0xFFFFF8E0),
            rankLabelColor = if (dark) Color(0xFFFFF0A0) else Color(0xFF6A5800),
        )
        2 -> PodiumStyle(
            borderColor = if (dark) Color(0xFF00F0FF).copy(alpha = 0.5f) else Color(0xFF007A8C).copy(alpha = 0.4f),
            glowColor = if (dark) Color(0xFF00F0FF) else Color(0xFF0099AA),
            containerTint = if (dark) Color(0xFF001828) else Color(0xFFE0F8FF),
            rankLabelColor = if (dark) Color(0xFFB8FCFF) else Color(0xFF004858),
        )
        3 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFFF2A6D).copy(alpha = 0.5f) else Color(0xFFC4005A).copy(alpha = 0.4f),
            glowColor = if (dark) Color(0xFFFF2A6D) else Color(0xFFC4005A),
            containerTint = if (dark) Color(0xFF280818) else Color(0xFFFFE8F0),
            rankLabelColor = if (dark) Color(0xFFFFB8D0) else Color(0xFF6A1030),
        )
        else -> null
    }

@Composable
private fun podiumStyleForRank(rank: Int): PodiumStyle? =
    podiumStyleForRank(rank, isRpsDarkTheme())

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
    val glowEdgeStrength = 0.18f

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
    val color = leaderboardRankLabelColor(rank, isRpsDarkTheme())
    return if (color == Color.Unspecified) fallback else color
}
