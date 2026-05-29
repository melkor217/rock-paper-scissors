package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.rpsonline.app.data.model.Match
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Single Firestore subscription for the signed-in user's active match and queue doc.
 * Shared by [MatchRepository], [HomeViewModel], and global UI effects in [RpsApp].
 */
object MatchSessionMonitor {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = appFirestore()

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    private val _queueJoinedAtMs = MutableStateFlow<Long?>(null)
    val queueJoinedAtMs: StateFlow<Long?> = _queueJoinedAtMs.asStateFlow()

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
     * Reconnects snapshot listeners and pulls queue/match from the server.
     * Call when the app returns to the foreground.
     */
    suspend fun refreshOnResume() {
        ensureStarted()
        val uid = auth.currentUser?.uid ?: return
        FirestoreConnectivity.restoreOnResume()
        reattachListeners(uid)
        syncFromServer(uid)
    }

    private fun attachForUser(uid: String?) {
        if (uid == null) {
            attachedUid = null
            clearFirestoreListeners()
            _activeMatch.value = null
            _queueJoinedAtMs.value = null
            return
        }
        if (uid == attachedUid) return
        attachedUid = uid
        clearFirestoreListeners()
        _activeMatch.value = null
        _queueJoinedAtMs.value = null
        attachListeners(uid)
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

        val queueSnap = runCatching {
            firestore.collection("queue").document(uid).get(Source.SERVER).await()
        }.getOrNull()
        _queueJoinedAtMs.value = when {
            queueSnap == null || !queueSnap.exists() -> null
            else -> queueSnap.getTimestamp("joinedAt")?.toDate()?.time
                ?: queueSnap.getLong("clientJoinedAt")
                ?: System.currentTimeMillis()
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
            _activeMatch.value = matchSnap.toMatch(matchId)
        }
    }

    private fun attachListeners(uid: String) {
        queueListener = firestore.collection("queue").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    _queueJoinedAtMs.value = null
                    return@addSnapshotListener
                }
                _queueJoinedAtMs.value = snapshot.getTimestamp("joinedAt")?.toDate()?.time
                    ?: snapshot.getLong("clientJoinedAt")
                    ?: System.currentTimeMillis()
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

    private fun attachMatchListener(matchId: String) {
        matchListener = firestore.collection("matches").document(matchId)
            .addSnapshotListener { matchSnapshot, matchError ->
                if (matchError != null) {
                    _activeMatch.value = null
                    return@addSnapshotListener
                }
                _activeMatch.value = matchSnapshot?.toMatch(matchId)
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
        _queueJoinedAtMs.value = null
    }
}
