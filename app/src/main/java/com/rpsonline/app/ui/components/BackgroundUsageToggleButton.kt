package com.rpsonline.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rpsonline.app.R

@Composable
fun BackgroundUsageToggleButton(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopBarSegmentedIconButton(
        onClick = onToggle,
        icon = if (enabled) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryFull,
        active = enabled,
        contentDescription = stringResource(
            if (enabled) {
                R.string.background_usage_enabled
            } else {
                R.string.background_usage_disabled
            },
        ),
        modifier = modifier,
    )
}
