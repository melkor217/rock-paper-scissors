package com.rpsonline.app.data.monitoring

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Measures round-trip latency to the `ping` Cloud Function.
 * Firestore document reads were too fast (1–7ms) when the user doc was already
 * synced via active listeners; a callable forces a real network round trip.
 */
class FirebaseConnectionMonitor(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<FirebaseConnectionStatus>(FirebaseConnectionStatus.Checking)
    val status: StateFlow<FirebaseConnectionStatus> = _status.asStateFlow()

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

    private suspend fun probeOnce(): FirebaseConnectionStatus {
        if (!hasValidatedNetwork()) return FirebaseConnectionStatus.Offline
        val user = auth.currentUser ?: return FirebaseConnectionStatus.Unauthenticated
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                val start = SystemClock.elapsedRealtime()
                // Force a real network round trip by bypassing cache.
                firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .get(Source.SERVER)
                    .await()
                val latencyMs = (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
                FirebaseConnectionStatus.Connected(latencyMs)
            }.getOrElse { FirebaseConnectionStatus.Unreachable }
        } ?: FirebaseConnectionStatus.Unreachable
    }

    private fun hasValidatedNetwork(): Boolean {
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val PROBE_INTERVAL_MS = 12_000L
        private const val PROBE_TIMEOUT_MS = 8_000L
    }
}
