package com.rpsonline.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun ClockSoundMuteButton(
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onMutedChange(!muted) },
        modifier = modifier.semantics {
            contentDescription = if (muted) "Unmute clock sound" else "Mute clock sound"
        },
    ) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
