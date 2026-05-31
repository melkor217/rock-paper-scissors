package com.rpsonline.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rpsonline.app.R

@Composable
fun MatchFoundNotificationToggleButton(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopBarSegmentedIconButton(
        onClick = onToggle,
        icon = if (enabled) Icons.Outlined.NotificationImportant else Icons.Outlined.NotificationsOff,
        active = enabled,
        contentDescription = stringResource(
            if (enabled) {
                R.string.match_found_notifications_enabled
            } else {
                R.string.match_found_notifications_disabled
            },
        ),
        modifier = modifier,
    )
}
