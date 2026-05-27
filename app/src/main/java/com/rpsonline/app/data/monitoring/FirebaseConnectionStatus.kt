package com.rpsonline.app.data.monitoring

sealed interface FirebaseConnectionStatus {
    data object Checking : FirebaseConnectionStatus
    data object Offline : FirebaseConnectionStatus
    data object Unauthenticated : FirebaseConnectionStatus
    data object Unreachable : FirebaseConnectionStatus
    data class Connected(val latencyMs: Int) : FirebaseConnectionStatus
}
