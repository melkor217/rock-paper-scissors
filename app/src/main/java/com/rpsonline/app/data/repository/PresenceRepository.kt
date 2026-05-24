package com.rpsonline.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PresenceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Best-effort; failures are logged by Firestore and must not crash the app. */
    suspend fun touchPresence(uid: String) {
        awaitFirestoreAuth()
        val now = Timestamp.now()
        firestore.collection(COLLECTION)
            .document(uid)
            .set(mapOf("lastSeen" to now))
        firestore.collection("users")
            .document(uid)
            .update(mapOf("lastSeen" to now))
    }

    suspend fun clearPresence(uid: String) {
        runCatching {
            firestore.collection(COLLECTION)
                .document(uid)
                .delete()
        }
    }

    fun observeOnlineCount(
        onlineWindowMs: Long = ONLINE_WINDOW_MS,
        refreshIntervalMs: Long = PRESENCE_REFRESH_MS,
    ): Flow<Int> = callbackFlow {
        var latestDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

        fun emitCount() {
            val cutoff = System.currentTimeMillis() - onlineWindowMs
            val count = latestDocs.count { doc ->
                doc.getTimestamp("lastSeen")?.toDate()?.time?.let { it >= cutoff } == true
            }
            trySend(count)
        }

        val listener = firestore.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                latestDocs = snapshot?.documents.orEmpty()
                emitCount()
            }

        val ticker = launch {
            while (isActive) {
                delay(refreshIntervalMs)
                emitCount()
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
        const val PRESENCE_REFRESH_MS = 30_000L
    }
}
