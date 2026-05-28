package com.rpsonline.app.data.monitoring

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Monitors whether the device has validated internet connectivity. */
class NetworkConnectionMonitor(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<NetworkConnectionStatus>(NetworkConnectionStatus.Checking)
    val status: StateFlow<NetworkConnectionStatus> = _status.asStateFlow()

    private var probeJob: Job? = null

    fun start(scope: CoroutineScope) {
        probeJob?.cancel()
        probeJob = scope.launch {
            while (isActive) {
                _status.value = probeOnce()
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        probeJob?.cancel()
        probeJob = null
    }

    private fun probeOnce(): NetworkConnectionStatus {
        return if (hasValidatedNetwork()) {
            NetworkConnectionStatus.Connected
        } else {
            NetworkConnectionStatus.Offline
        }
    }

    private fun hasValidatedNetwork(): Boolean {
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        val hasUsableTransport =
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return hasInternet && (isValidated || (hasUsableTransport && !isCaptivePortal))
    }

    companion object {
        private const val PROBE_INTERVAL_MS = 12_000L
    }
}
