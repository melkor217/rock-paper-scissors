package com.rpsonline.app.data.monitoring

sealed interface NetworkConnectionStatus {
    data object Checking : NetworkConnectionStatus
    data object Offline : NetworkConnectionStatus
    data object Connected : NetworkConnectionStatus
}
