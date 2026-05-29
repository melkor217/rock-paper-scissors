package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Single Firestore subscription for the signed-in user's active match and queue doc.
 * Shared by [MatchRepository], [HomeViewModel], and global UI effects in [RpsApp].
 */
object MatchSessionMonitor {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = appFirestore()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val matchRepository = MatchRepository()

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    private val _queueJoinedAtMs = MutableStateFlow<Long?>(null)
    val queueJoinedAtMs: StateFlow<Long?> = _queueJoinedAtMs.asStateFlow()

    /** Queue doc exists locally; heartbeats run while this is true. */
    private val _hasQueueEntry = MutableStateFlow(false)
    val hasQueueEntry: StateFlow<Boolean> = _hasQueueEntry.asStateFlow()

    fun isQueueEntryPending(): Boolean = _hasQueueEntry.value && _queueJoinedAtMs.value == null

    /** Set while the user is joining or waiting in queue; drives auto-navigation to game. */
    private val _matchmakingInProgress = MutableStateFlow(false)
    val matchmakingInProgress: StateFlow<Boolean> = _matchmakingInProgress.asStateFlow()

    /** Pending navigation to game; survives HomeViewModel / back-stack lifecycle. */
    private val _pendingGameNavigationMatchId = MutableStateFlow<String?>(null)
    val pendingGameNavigationMatchId: StateFlow<String?> = _pendingGameNavigationMatchId.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var userListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null
    private var attachedUid: String? = null

    fun ensureStarted() {
        if (authListener != null) return
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            attachForUser(firebaseAuth.currentUser?.uid)
        }
        authListener = listener
        auth.addAuthStateListener(listener)
        attachForUser(auth.currentUser?.uid)
    }

    /**
     * Reconnects snapshot listeners and pulls match from the server.
     * Stale queue entries are cleared unless matchmaking is actively in progress.
     */
    suspend fun refreshOnResume() {
        ensureStarted()
        val uid = auth.currentUser?.uid ?: return
        FirestoreConnectivity.restoreOnResume()
        reattachListeners(uid)
        syncFromServer(uid)
    }

    fun setMatchmakingInProgress(active: Boolean) {
        _matchmakingInProgress.value = active
    }

    fun isMatchmakingInProgress(): Boolean = _matchmakingInProgress.value

    fun requestGameNavigation(matchId: String) {
        _pendingGameNavigationMatchId.value = matchId
    }

    fun consumeGameNavigation() {
        _pendingGameNavigationMatchId.value = null
    }

    /** Called when queue entry is confirmed (server or client join timestamp). */
    fun confirmQueueJoinedAt(joinedAtMs: Long) {
        _hasQueueEntry.value = true
        _queueJoinedAtMs.value = joinedAtMs
    }

    private fun attachForUser(uid: String?) {
        if (uid == null) {
            val leaving = attachedUid
            attachedUid = null
            clearFirestoreListeners()
            resetSessionUiState()
            if (leaving != null) {
                matchRepository.clearStaleSessionQueueBestEffort(leaving)
            }
            return
        }
        if (uid == attachedUid) return

        val previous = attachedUid
        attachedUid = uid
        clearFirestoreListeners()
        resetSessionUiState()
        if (previous != null && previous != uid) {
            matchRepository.clearStaleSessionQueueBestEffort(previous)
        }
        val bootstrap = FirestoreSessionGate.startBootstrap()
        sessionScope.launch {
            try {
                matchRepository.clearStaleSessionQueue(uid)
            } finally {
                FirestoreSessionGate.finishBootstrap(bootstrap)
            }
        }
        attachListeners(uid)
    }

    /** Waits for post-auth queue cleanup so join/presence writes are not raced. */
    suspend fun awaitSessionBootstrap() {
        FirestoreSessionGate.awaitBootstrap()
    }

    private fun resetSessionUiState() {
        _activeMatch.value = null
        _queueJoinedAtMs.value = null
        _hasQueueEntry.value = false
        _matchmakingInProgress.value = false
        _pendingGameNavigationMatchId.value = null
    }

    private fun reattachListeners(uid: String) {
        attachedUid = uid
        clearFirestoreListeners()
        attachListeners(uid)
    }

    private suspend fun syncFromServer(uid: String) {
        val userSnap = runCatching {
            firestore.collection("users").document(uid).get(Source.SERVER).await()
        }.getOrNull() ?: return

        if (_matchmakingInProgress.value) {
            val queueSnap = runCatching {
                firestore.collection("queue").document(uid).get(Source.SERVER).await()
            }.getOrNull()
            val queueExists = queueSnap != null && queueSnap.exists()
            _hasQueueEntry.value = queueExists
            _queueJoinedAtMs.value = if (queueExists) {
                resolveQueueJoinedAtMs(queueSnap!!)
            } else {
                null
            }
        } else {
            runCatching { matchRepository.clearStaleSessionQueue(uid) }
        }

        val matchId = userSnap.getString("activeMatchId")
        if (matchId.isNullOrBlank()) {
            _activeMatch.value = null
            return
        }

        val matchSnap = runCatching {
            firestore.collection("matches").document(matchId).get(Source.SERVER).await()
        }.getOrNull() ?: return
        if (matchSnap.exists()) {
            publishActiveMatch(matchSnap.toMatch(matchId))
        }
    }

    private fun attachListeners(uid: String) {
        queueListener = firestore.collection("queue").document(uid)
            .addSnapshotListener { snapshot, error ->
                applyQueueSnapshot(snapshot, error)
            }

        userListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _activeMatch.value = null
                    return@addSnapshotListener
                }

                matchListener?.remove()
                matchListener = null

                val matchId = snapshot?.getString("activeMatchId")
                if (matchId.isNullOrBlank()) {
                    _activeMatch.value = null
                    return@addSnapshotListener
                }

                attachMatchListener(matchId)
            }
    }

    private fun applyQueueSnapshot(snapshot: DocumentSnapshot?, error: Exception?) {
        if (error != null || snapshot == null || !snapshot.exists()) {
            _hasQueueEntry.value = false
            _queueJoinedAtMs.value = null
            return
        }
        if (!_matchmakingInProgress.value) {
            _hasQueueEntry.value = false
            _queueJoinedAtMs.value = null
            return
        }
        _hasQueueEntry.value = true
        resolveQueueJoinedAtMs(snapshot)?.let { joinedAtMs ->
            _queueJoinedAtMs.value = joinedAtMs
        }
        if (snapshot.metadata.hasPendingWrites()) {
            return
        }
    }

    private fun resolveQueueJoinedAtMs(snapshot: DocumentSnapshot): Long? {
        return snapshot.getTimestamp("joinedAt")?.toDate()?.time
            ?: snapshot.getLong("clientJoinedAt")?.takeIf { it > 0L }
    }

    private fun attachMatchListener(matchId: String) {
        matchListener = firestore.collection("matches").document(matchId)
            .addSnapshotListener { matchSnapshot, matchError ->
                if (matchError != null) {
                    _activeMatch.value = null
                    return@addSnapshotListener
                }
                publishActiveMatch(matchSnapshot?.toMatch(matchId))
            }
    }

    private fun publishActiveMatch(match: Match?) {
        _activeMatch.value = match
        val uid = auth.currentUser?.uid ?: return
        if (
            match?.status == MatchStatus.ACTIVE &&
            match.isParticipant(uid) &&
            _matchmakingInProgress.value
        ) {
            requestGameNavigation(match.id)
        }
    }

    private fun clearFirestoreListeners() {
        queueListener?.remove()
        queueListener = null
        userListener?.remove()
        userListener = null
        matchListener?.remove()
        matchListener = null
    }

    /** Local fallback when queue heartbeat fails and snapshot lag leaves stale UI. */
    fun clearQueueState() {
        _hasQueueEntry.value = false
        _queueJoinedAtMs.value = null
    }
}
