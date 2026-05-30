package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpsonline.app.R
import com.rpsonline.app.data.monitoring.NetworkConnectionStatus

val LocalNetworkConnectionStatus = compositionLocalOf<NetworkConnectionStatus> {
    NetworkConnectionStatus.Checking
}

fun NetworkConnectionStatus.isServerConnected(): Boolean = this == NetworkConnectionStatus.Connected

@Composable
fun NetworkConnectionIndicator(
    status: NetworkConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, iconColor, description) = indicatorPresentation(status)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = modifier
            .height(28.dp)
            .size(20.dp)
            .semantics { contentDescription = description }
            .padding(horizontal = 2.dp),
    )
}

private data class IndicatorPresentation(
    val icon: ImageVector,
    val iconColor: Color,
    val description: String,
)

@Composable
private fun indicatorPresentation(status: NetworkConnectionStatus): IndicatorPresentation {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        NetworkConnectionStatus.Connected -> IndicatorPresentation(
            icon = Icons.Outlined.SignalWifi4Bar,
            iconColor = scheme.primary,
            description = stringResource(R.string.connection_indicator_online),
        )
        NetworkConnectionStatus.Checking -> IndicatorPresentation(
            icon = Icons.Outlined.SignalWifi4Bar,
            iconColor = scheme.onSurfaceVariant,
            description = stringResource(R.string.connection_indicator_checking),
        )
        NetworkConnectionStatus.Offline -> IndicatorPresentation(
            icon = Icons.Outlined.SignalWifiOff,
            iconColor = scheme.error,
            description = stringResource(R.string.connection_indicator_offline),
        )
    }
}
