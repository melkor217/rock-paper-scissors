package com.rpsonline.app.data.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionReachabilityPolicyTest {

    @Test
    fun offlineWhenNetworkDown() {
        assertEquals(
            NetworkConnectionStatus.Offline,
            ConnectionReachabilityPolicy.resolveStatus(
                hasNetwork = false,
                serverReachable = true,
                nowMs = 100_000L,
                lastServerSuccessMs = 50_000L,
            ),
        )
    }

    @Test
    fun connectedWhenServerReachable() {
        assertEquals(
            NetworkConnectionStatus.Connected,
            ConnectionReachabilityPolicy.resolveStatus(
                hasNetwork = true,
                serverReachable = true,
                nowMs = 100_000L,
                lastServerSuccessMs = 0L,
            ),
        )
    }

    @Test
    fun checkingBeforeFirstServerSuccess() {
        assertEquals(
            NetworkConnectionStatus.Checking,
            ConnectionReachabilityPolicy.resolveStatus(
                hasNetwork = true,
                serverReachable = false,
                nowMs = 100_000L,
                lastServerSuccessMs = 0L,
            ),
        )
    }

    @Test
    fun staysConnectedDuringBriefOutage() {
        val lastSuccess = 100_000L
        assertEquals(
            NetworkConnectionStatus.Connected,
            ConnectionReachabilityPolicy.resolveStatus(
                hasNetwork = true,
                serverReachable = false,
                nowMs = lastSuccess + 30_000L,
                lastServerSuccessMs = lastSuccess,
            ),
        )
    }

    @Test
    fun offlineAfterStaleServerWindow() {
        val lastSuccess = 100_000L
        assertEquals(
            NetworkConnectionStatus.Offline,
            ConnectionReachabilityPolicy.resolveStatus(
                hasNetwork = true,
                serverReachable = false,
                nowMs = lastSuccess + ConnectionReachabilityPolicy.SERVER_STALE_MS + 1,
                lastServerSuccessMs = lastSuccess,
            ),
        )
    }
}
