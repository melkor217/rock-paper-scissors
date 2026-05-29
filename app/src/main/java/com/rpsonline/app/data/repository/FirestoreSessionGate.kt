package com.rpsonline.app.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes Firestore writes that conflict when run together (queue join vs session cleanup vs presence).
 */
internal object FirestoreSessionGate {
    private val mutex = Mutex()
    @Volatile
    private var bootstrap: CompletableDeferred<Unit>? = null

    suspend fun <T> withWriteLock(block: suspend () -> T): T = mutex.withLock { block() }

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
