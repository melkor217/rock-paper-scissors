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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await

class PresenceRepository(
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    /**
     * Writes [COLLECTION]/[uid] so other clients can count this player as online.
     * Retries once with a fresh auth token — Google sign-in often needs that on the first heartbeat.
     */
    suspend fun touchPresence(uid: String, forceAuthRefresh: Boolean = false) {
        val now = Timestamp.now()
        val payload = mapOf("lastSeen" to now)
        val presenceRef = firestore.collection(COLLECTION).document(uid)

        for (attempt in 0..1) {
            awaitFirestoreAuth(forceRefresh = forceAuthRefresh || attempt > 0)
            val wrote = runCatching {
                withTimeout(PRESENCE_WRITE_TIMEOUT_MS) {
                    presenceRef.set(payload).awaitTask()
                }
            }.isSuccess
            if (wrote) return
            delay(350)
        }

        firestore.collection("users")
            .document(uid)
            .updateBestEffort(payload)
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

    fun observeOnlineCount(
        onlineWindowMs: Long = ONLINE_WINDOW_MS,
    ): Flow<Int> = callbackFlow {
        var latestDocs: List<DocumentSnapshot> = emptyList()

        suspend fun loadFromServer() {
            awaitFirestoreAuth()
            latestDocs = firestore.collection(COLLECTION).get(Source.SERVER).await().documents
        }

        fun emitCount() {
            val count = countOnlineDocuments(latestDocs, onlineWindowMs)
            trySend(count)
        }

        fun recoverFromServer() {
            launch {
                runCatching { loadFromServer() }
                emitCount()
            }
        }

        emitCount()

        val listener = firestore.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    recoverFromServer()
                    return@addSnapshotListener
                }
                latestDocs = snapshot?.documents.orEmpty()
                emitCount()
            }

        val recalcTicker = launch {
            while (isActive) {
                delay(COUNT_RECALC_INTERVAL_MS)
                emitCount()
            }
        }

        val serverSyncTicker = launch {
            while (isActive) {
                delay(SERVER_SYNC_INTERVAL_MS)
                recoverFromServer()
            }
        }

        recoverFromServer()

        awaitClose {
            recalcTicker.cancel()
            serverSyncTicker.cancel()
            listener.remove()
        }
    }

    companion object {
        const val COLLECTION = "presence"
        const val ONLINE_WINDOW_MS = 2 * 60 * 1000L
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        /** Re-count online players as lastSeen ages out without waiting for doc changes. */
        private const val COUNT_RECALC_INTERVAL_MS = 10_000L
        /** Refresh presence docs from server in case the snapshot listener went stale. */
        private const val SERVER_SYNC_INTERVAL_MS = 45_000L
        private const val PRESENCE_WRITE_TIMEOUT_MS = 8_000L

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
