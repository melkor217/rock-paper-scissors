package com.rpsonline.app.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Serializes queue join/leave/cleanup so they do not race each other. */
internal object QueueWriteGate {
    private val mutex = Mutex()
    @Volatile
    private var bootstrap: CompletableDeferred<Unit>? = null

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun startBootstrap(): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        bootstrap = deferred
        return deferred
    }

    fun finishBootstrap(deferred: CompletableDeferred<Unit>) {
        deferred.complete(Unit)
        if (bootstrap === deferred) {
            bootstrap = null
        }
    }

    suspend fun awaitBootstrap(timeoutMs: Long = 15_000) {
        val deferred = bootstrap ?: return
        withTimeoutOrNull(timeoutMs) { deferred.await() }
    }
}
