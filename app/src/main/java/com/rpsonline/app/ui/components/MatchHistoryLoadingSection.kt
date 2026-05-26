package com.rpsonline.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun MatchHistoryLoadingSection(
    modifier: Modifier = Modifier,
    placeholderCount: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(placeholderCount) {
            MatchHistoryCardSkeleton()
        }
    }
}

@Composable
private fun MatchHistoryCardSkeleton() {
    val pulseAlpha by rememberInfiniteTransition(label = "matchHistorySkeleton")
        .animateFloat(
            initialValue = 0.28f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        )

    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBar(width = 72.dp, height = 10.dp, alpha = pulseAlpha)
                SkeletonBar(width = 48.dp, height = 14.dp, alpha = pulseAlpha)
                SkeletonBar(width = 56.dp, height = 10.dp, alpha = pulseAlpha)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonBar(width = 88.dp, height = 12.dp, alpha = pulseAlpha)
                    SkeletonBar(width = 36.dp, height = 10.dp, alpha = pulseAlpha)
                }
                SkeletonBar(width = 40.dp, height = 12.dp, alpha = pulseAlpha)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkeletonBar(width = 88.dp, height = 12.dp, alpha = pulseAlpha)
                    SkeletonBar(width = 36.dp, height = 10.dp, alpha = pulseAlpha)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(alpha),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    ) {
        Box(modifier = Modifier.fillMaxWidth())
    }
}
