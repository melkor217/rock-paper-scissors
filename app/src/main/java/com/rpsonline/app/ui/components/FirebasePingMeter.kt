package com.rpsonline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.monitoring.FirebaseConnectionStatus
import com.rpsonline.app.domain.firebasePingBarCount

@Composable
fun FirebasePingMeter(
    status: FirebaseConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val (activeBars, barColor, label, description) = meterPresentation(status)

    Row(
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PingBars(
            activeBars = activeBars,
            activeColor = barColor,
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PingBars(
    activeBars: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val heights = listOf(7.dp, 9.dp, 12.dp, 14.dp)
    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heights.forEachIndexed { index, barHeight ->
            val color = if (index < activeBars) activeColor else inactiveColor
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

private data class MeterPresentation(
    val activeBars: Int,
    val barColor: Color,
    val label: String,
    val description: String,
)

@Composable
private fun meterPresentation(status: FirebaseConnectionStatus): MeterPresentation {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        FirebaseConnectionStatus.Checking -> MeterPresentation(
            activeBars = 1,
            barColor = scheme.onSurfaceVariant,
            label = "…",
            description = "Checking Firebase server connection",
        )
        FirebaseConnectionStatus.Offline -> MeterPresentation(
            activeBars = 0,
            barColor = scheme.error,
            label = "Off",
            description = "No network connection",
        )
        FirebaseConnectionStatus.Unauthenticated -> MeterPresentation(
            activeBars = 0,
            barColor = scheme.onSurfaceVariant,
            label = "—",
            description = "Sign in to measure Firebase latency",
        )
        FirebaseConnectionStatus.Unreachable -> MeterPresentation(
            activeBars = 0,
            barColor = scheme.error,
            label = "Err",
            description = "Cannot reach Firebase servers",
        )
        is FirebaseConnectionStatus.Connected -> {
            val bars = firebasePingBarCount(status.latencyMs)
            val color = when (bars) {
                4, 3 -> scheme.primary
                2 -> scheme.tertiary
                else -> scheme.error
            }
            MeterPresentation(
                activeBars = bars,
                barColor = color,
                label = "${status.latencyMs}ms",
                description = "Firebase server round trip ${status.latencyMs} milliseconds",
            )
        }
    }
}
