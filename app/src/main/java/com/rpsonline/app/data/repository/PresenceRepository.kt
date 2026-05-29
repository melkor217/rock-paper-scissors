package com.rpsonline.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class PresenceRepository(
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    /**
     * Writes [COLLECTION]/[uid] so other clients can count this player as online.
     * Heartbeats use a fast write; sign-in / resume use [awaitServerAck] so the count is accurate.
     */
    suspend fun touchPresence(
        uid: String,
        forceAuthRefresh: Boolean = false,
        awaitServerAck: Boolean = false,
    ) {
        val payload = mapOf("lastSeen" to Timestamp.now())
        val presenceRef = firestore.collection(COLLECTION).document(uid)
        val attempts = if (awaitServerAck) 3 else 1

        for (attempt in 0 until attempts) {
            val wrote = runCatching {
                awaitFirestoreAuth(forceRefresh = forceAuthRefresh || attempt > 0)
                if (awaitServerAck) {
                    presenceRef.setAndAwaitServerSync(
                        data = payload,
                        writeTimeoutMs = PRESENCE_WRITE_TIMEOUT_MS,
                        syncTimeoutMs = PRESENCE_SYNC_TIMEOUT_MS,
                    )
                } else {
                    withTimeout(PRESENCE_FAST_WRITE_TIMEOUT_MS) {
                        presenceRef.set(payload).awaitTask()
                    }
                }
            }.isSuccess
            if (wrote) {
                firestore.collection("users").document(uid).updateBestEffort(payload)
                return
            }
            if (awaitServerAck) delay(400)
        }
    }

    fun clearPresence(uid: String) {
        firestore.collection(COLLECTION)
            .document(uid)
            .deleteBestEffort()
    }

    suspend fun fetchOnlineCountFromServer(
        onlineWindowMs: Long = ONLINE_WINDOW_MS,
    ): Int {
        awaitFirestoreAuth()
        val snapshot = firestore.collection(COLLECTION).get(Source.SERVER).await()
        return countOnlineDocuments(snapshot.documents, onlineWindowMs)
    }

    /** All clients use the same server-backed count so Google vs guest auth cannot diverge. */
    fun observeOnlineCount(
        onlineWindowMs: Long = ONLINE_WINDOW_MS,
    ): Flow<Int> = callbackFlow {
        var lastEmitted: Int? = null

        suspend fun emitFromServer() {
            val count = runCatching { fetchOnlineCountFromServer(onlineWindowMs) }.getOrNull()
                ?: return
            if (count != lastEmitted) {
                lastEmitted = count
                trySend(count)
            }
        }

        emitFromServer()

        val listener = firestore.collection(COLLECTION).addSnapshotListener { _, _ ->
            launch { emitFromServer() }
        }

        val ticker = launch {
            while (isActive) {
                delay(SERVER_POLL_INTERVAL_MS)
                emitFromServer()
            }
        }

        awaitClose {
            ticker.cancel()
            listener.remove()
        }
    }

    companion object {
        const val COLLECTION = "presence"
        const val ONLINE_WINDOW_MS = 2 * 60 * 1000L
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val PRESENCE_WRITE_TIMEOUT_MS = 15_000L
        private const val PRESENCE_SYNC_TIMEOUT_MS = 18_000L
        private const val PRESENCE_FAST_WRITE_TIMEOUT_MS = 5_000L
        private const val SERVER_POLL_INTERVAL_MS = 8_000L

        fun countOnlineDocuments(
            documents: List<DocumentSnapshot>,
            onlineWindowMs: Long = ONLINE_WINDOW_MS,
        ): Int {
            val cutoff = System.currentTimeMillis() - onlineWindowMs
            return documents.count { doc ->
                doc.getTimestamp("lastSeen")?.toDate()?.time?.let { it >= cutoff } == true
            }
        }
    }
}
