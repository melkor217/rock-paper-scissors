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

@Composable
private fun podiumStyleForRank(rank: Int): PodiumStyle? {
    val dark = isSystemInDarkTheme()
    return when (rank) {
        1 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFFFD54F) else Color(0xFFFFB300),
            glowColor = if (dark) Color(0xFFFFCA28) else Color(0xFFFFA000),
            containerTint = if (dark) Color(0xFF5D4037) else Color(0xFFFFF8E1),
            rankLabelColor = if (dark) Color(0xFFFFE082) else Color(0xFFF57F17),
        )
        2 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFE0E0E0) else Color(0xFF90A4AE),
            glowColor = if (dark) Color(0xFFB0BEC5) else Color(0xFF78909C),
            containerTint = if (dark) Color(0xFF37474F) else Color(0xFFECEFF1),
            rankLabelColor = if (dark) Color(0xFFECEFF1) else Color(0xFF546E7A),
        )
        3 -> PodiumStyle(
            borderColor = if (dark) Color(0xFFFFAB91) else Color(0xFF8D6E63),
            glowColor = if (dark) Color(0xFFFF8A65) else Color(0xFFA1887F),
            containerTint = if (dark) Color(0xFF4E342E) else Color(0xFFEFEBE9),
            rankLabelColor = if (dark) Color(0xFFFFCCBC) else Color(0xFF6D4C41),
        )
        else -> null
    }
}

private fun DrawScope.drawInwardPodiumGlow(glowColor: Color, cornerRadius: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = max(size.width, size.height) * 0.72f
    val corner = CornerRadius(cornerRadius, cornerRadius)

    drawRoundRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.42f to glowColor.copy(alpha = 0.05f),
                0.72f to glowColor.copy(alpha = 0.24f),
                1.0f to glowColor.copy(alpha = 0.52f),
            ),
            center = center,
            radius = radius,
        ),
        size = size,
        cornerRadius = corner,
    )

    val edgeStrength = 0.38f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to glowColor.copy(alpha = edgeStrength),
                0.22f to Color.Transparent,
                0.78f to Color.Transparent,
                1.0f to glowColor.copy(alpha = edgeStrength),
            ),
            startX = 0f,
            endX = size.width,
        ),
        size = size,
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to glowColor.copy(alpha = edgeStrength * 0.85f),
                0.28f to Color.Transparent,
                0.72f to Color.Transparent,
                1.0f to glowColor.copy(alpha = edgeStrength * 0.85f),
            ),
            startY = 0f,
            endY = size.height,
        ),
        size = size,
        cornerRadius = corner,
    )
}

@Composable
fun LeaderboardEntryCard(
    rank: Int,
    modifier: Modifier = Modifier,
    isCurrentUser: Boolean = false,
    content: @Composable () -> Unit,
) {
    val podium = podiumStyleForRank(rank)
    val shape = MaterialTheme.shapes.medium
    val baseContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    var containerColor = podium?.let { lerp(baseContainer, it.containerTint, 0.18f) } ?: baseContainer
    if (isCurrentUser) {
        containerColor = lerp(containerColor, MaterialTheme.colorScheme.primaryContainer, 0.35f)
    }
    val border = when {
        podium != null -> BorderStroke(1.5.dp, podium.borderColor)
        isCurrentUser -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    val cornerRadius = 12.dp

    Card(
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
                    if (podium != null) {
                        Modifier.drawBehind {
                            drawInwardPodiumGlow(
                                glowColor = podium.glowColor,
                                cornerRadius = cornerRadius.toPx(),
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

@Composable
fun leaderboardRankLabelColor(rank: Int): Color =
    podiumStyleForRank(rank)?.rankLabelColor ?: MaterialTheme.colorScheme.onSurface
