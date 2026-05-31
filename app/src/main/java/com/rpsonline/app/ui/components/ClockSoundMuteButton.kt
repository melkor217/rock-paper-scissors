package com.rpsonline.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ClockSoundMuteButton(
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TopBarSegmentedIconButton(
        onClick = { onMutedChange(!muted) },
        icon = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
        active = !muted,
        contentDescription = if (muted) "Unmute clock sound" else "Mute clock sound",
        modifier = modifier,
    )
}
