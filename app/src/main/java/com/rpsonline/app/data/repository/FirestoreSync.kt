package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Waits until queued Firestore writes are committed or rejected by the server. */
internal suspend fun FirebaseFirestore.awaitPendingWritesSynced(timeoutMs: Long = 18_000) {
    withTimeout(timeoutMs) {
        waitForPendingWrites().awaitTask()
    }
}

/**
 * Writes then polls this document on the server. Unlike [waitForPendingWrites], this does not
 * block on unrelated pending writes (e.g. presence heartbeats).
 */
internal suspend fun DocumentReference.setAndConfirmOnServer(
    data: Map<String, Any>,
    writeTimeoutMs: Long = 10_000,
    confirmTimeoutMs: Long = 12_000,
    pollIntervalMs: Long = 300L,
) {
    withTimeout(writeTimeoutMs) {
        set(data).awaitTask()
    }
    if (!confirmExistsOnServer(confirmTimeoutMs, pollIntervalMs)) {
        error("Timed out waiting for the server to accept the write")
    }
}

internal suspend fun DocumentReference.confirmExistsOnServer(
    confirmTimeoutMs: Long = 12_000,
    pollIntervalMs: Long = 300L,
): Boolean {
    val deadline = System.currentTimeMillis() + confirmTimeoutMs
    while (System.currentTimeMillis() < deadline) {
        val exists = withTimeoutOrNull(4_000) {
            get(Source.SERVER).await().exists()
        }
        if (exists == true) return true
        delay(pollIntervalMs)
    }
    return false
}

internal suspend fun DocumentReference.setAndAwaitServerSync(
    data: Map<String, Any>,
    writeTimeoutMs: Long = 10_000,
    syncTimeoutMs: Long = 12_000,
) {
    setAndConfirmOnServer(data, writeTimeoutMs, syncTimeoutMs)
}
