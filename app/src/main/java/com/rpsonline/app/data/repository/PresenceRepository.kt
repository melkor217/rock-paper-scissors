package com.rpsonline.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

class PresenceRepository(
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    /** serverTimeMs - clientTimeMs; used so online-window checks match server timestamps. */
    @Volatile
    private var serverTimeOffsetMs: Long = 0L

    private fun currentTimeMs(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    private fun updateServerTimeOffset(serverTimeMs: Long) {
        serverTimeOffsetMs = serverTimeMs - System.currentTimeMillis()
    }

    /**
     * Writes [COLLECTION]/[uid] so other clients can count this player as online.
     * Prefers the [touchPresence] Cloud Function (server timestamp); falls back to Firestore.
     */
    suspend fun touchPresence(
        uid: String,
        forceAuthRefresh: Boolean = false,
        awaitServerAck: Boolean = false,
    ) {
        val serverTimeMs = PresenceFunctions.tryTouchPresence()
        if (serverTimeMs != null) {
            updateServerTimeOffset(serverTimeMs)
            return
        }
        touchPresenceViaFirestore(uid, forceAuthRefresh, awaitServerAck)
    }

    private suspend fun touchPresenceViaFirestore(
        uid: String,
        forceAuthRefresh: Boolean,
        awaitServerAck: Boolean,
    ) {
        val payload = mapOf("lastSeen" to Timestamp.now())
        val presenceRef = firestore.collection(COLLECTION).document(uid)
        val attempts = if (awaitServerAck) 3 else 1

        for (attempt in 0 until attempts) {
            val wrote = runCatching {
                awaitFirestoreAuth(forceRefresh = forceAuthRefresh || attempt > 0)
                withTimeout(PRESENCE_WRITE_TIMEOUT_MS) {
                    presenceRef.set(payload).awaitTask()
                }
                if (awaitServerAck) {
                    presenceRef.confirmPresenceFreshOnServer(
                        maxAgeMs = PRESENCE_ACK_MAX_AGE_MS,
                        confirmTimeoutMs = PRESENCE_SYNC_TIMEOUT_MS,
                    )
                } else {
                    true
                }
            }.getOrElse { false }
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
        selfUid: String? = null,
    ): Int {
        awaitFirestoreAuth()
        val nowMs = currentTimeMs()
        val cutoff = Timestamp(Date(nowMs - onlineWindowMs))
        val presenceSnap = withTimeout(FETCH_TIMEOUT_MS) {
            firestore.collection(COLLECTION).get(Source.SERVER).await()
        }
        val usersSnap = withTimeout(FETCH_TIMEOUT_MS) {
            firestore.collection("users")
                .whereGreaterThan("lastSeen", cutoff)
                .get(Source.SERVER)
                .await()
        }

        val onlineUids = mutableSetOf<String>()
        presenceSnap.documents.forEach { doc ->
            if (isDocumentOnline(doc, onlineWindowMs, nowMs)) {
                onlineUids.add(doc.id)
            }
        }
        usersSnap.documents.forEach { doc ->
            onlineUids.add(doc.id)
        }
        if (selfUid != null) {
            onlineUids.add(selfUid)
        }
        return onlineUids.size
    }

    /**
     * Server-backed count; polls on an interval and reacts to presence changes.
     * [selfUid] is always included while this session is active (avoids under-counting on clock skew).
     */
    fun observeOnlineCount(
        onlineWindowMs: Long = ONLINE_WINDOW_MS,
        selfUid: String? = null,
    ): Flow<Int> = callbackFlow {
        var lastEmitted: Int? = null
        var lastAuthRefreshMs = 0L
        val fetchMutex = Mutex()

        suspend fun emitCount(alwaysEmit: Boolean, forceAuthRefresh: Boolean) {
            val count = fetchMutex.withLock {
                withTimeoutOrNull(FETCH_TIMEOUT_MS + 2_000) {
                    runCatching {
                        val now = System.currentTimeMillis()
                        if (forceAuthRefresh || now - lastAuthRefreshMs >= AUTH_REFRESH_INTERVAL_MS) {
                            awaitFirestoreAuth(forceRefresh = forceAuthRefresh)
                            lastAuthRefreshMs = now
                        } else {
                            awaitFirestoreAuth()
                        }
                        fetchOnlineCountFromServer(onlineWindowMs, selfUid)
                    }.getOrNull()
                }
            } ?: return

            if (alwaysEmit || count != lastEmitted) {
                lastEmitted = count
                trySend(count)
            }
        }

        fun scheduleRefresh(alwaysEmit: Boolean, forceAuthRefresh: Boolean = false) {
            launch {
                emitCount(alwaysEmit = alwaysEmit, forceAuthRefresh = forceAuthRefresh)
            }
        }

        scheduleRefresh(alwaysEmit = true, forceAuthRefresh = true)

        val listeners = mutableListOf<ListenerRegistration>()

        listeners += firestore.collection(COLLECTION).addSnapshotListener { _, error ->
            if (error != null) {
                scheduleRefresh(alwaysEmit = true, forceAuthRefresh = true)
                return@addSnapshotListener
            }
            scheduleRefresh(alwaysEmit = false)
        }

        val ticker = launch {
            var tick = 0
            while (isActive) {
                delay(SERVER_POLL_INTERVAL_MS)
                tick++
                scheduleRefresh(
                    alwaysEmit = true,
                    forceAuthRefresh = tick % AUTH_REFRESH_EVERY_N_POLLS == 0,
                )
            }
        }

        awaitClose {
            ticker.cancel()
            listeners.forEach { it.remove() }
        }
    }

    companion object {
        const val COLLECTION = "presence"
        const val ONLINE_WINDOW_MS = 2 * 60 * 1000L
        const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val PRESENCE_WRITE_TIMEOUT_MS = 8_000L
        private const val PRESENCE_SYNC_TIMEOUT_MS = 10_000L
        private const val PRESENCE_ACK_MAX_AGE_MS = 90_000L
        private const val FETCH_TIMEOUT_MS = 10_000L
        private const val SERVER_POLL_INTERVAL_MS = 5_000L
        private const val AUTH_REFRESH_INTERVAL_MS = 3 * 60 * 1000L
        private const val AUTH_REFRESH_EVERY_N_POLLS = 36 // ~3 min at 5s poll
        /** Extra tolerance when comparing server lastSeen to a client-derived cutoff. */
        private const val ONLINE_WINDOW_GRACE_MS = 30_000L

        private fun isDocumentOnline(
            doc: DocumentSnapshot,
            onlineWindowMs: Long,
            nowMs: Long,
        ): Boolean {
            val cutoff = nowMs - onlineWindowMs - ONLINE_WINDOW_GRACE_MS
            return doc.getTimestamp("lastSeen")?.toDate()?.time?.let { it >= cutoff } == true
        }

        fun countOnlineDocuments(
            documents: List<DocumentSnapshot>,
            onlineWindowMs: Long = ONLINE_WINDOW_MS,
        ): Int {
            val nowMs = System.currentTimeMillis()
            return documents.count { isDocumentOnline(it, onlineWindowMs, nowMs) }
        }
    }
}
