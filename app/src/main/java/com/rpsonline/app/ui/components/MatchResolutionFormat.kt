package com.rpsonline.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rpsonline.app.data.model.ViewerMatchResolution

fun viewerMatchResolutionLabel(resolution: ViewerMatchResolution): String = when (resolution) {
    ViewerMatchResolution.ABANDONED -> "Cancelled"
    ViewerMatchResolution.DRAW -> "Draw"
    ViewerMatchResolution.WIN -> "Win"
    ViewerMatchResolution.LOSS -> "Loss"
}

@Composable
fun viewerMatchResolutionColor(resolution: ViewerMatchResolution): Color = when (resolution) {
    ViewerMatchResolution.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
    ViewerMatchResolution.DRAW -> MaterialTheme.colorScheme.tertiary
    ViewerMatchResolution.WIN -> MaterialTheme.colorScheme.primary
    ViewerMatchResolution.LOSS -> MaterialTheme.colorScheme.error
}
