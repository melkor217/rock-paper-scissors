package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.withTimeout

/** Waits until queued Firestore writes are committed or rejected by the server. */
internal suspend fun FirebaseFirestore.awaitPendingWritesSynced(timeoutMs: Long = 18_000) {
    withTimeout(timeoutMs) {
        waitForPendingWrites().awaitTask()
    }
}

internal suspend fun DocumentReference.setAndAwaitServerSync(
    data: Map<String, Any>,
    writeTimeoutMs: Long = 10_000,
    syncTimeoutMs: Long = 12_000,
) {
    withTimeout(writeTimeoutMs) {
        set(data).awaitTask()
    }
    firestore.awaitPendingWritesSynced(syncTimeoutMs)
}
