package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rpsonline.app.data.model.Match
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single Firestore subscription for the signed-in user's active match and queue doc.
 * Shared by [MatchRepository], [HomeViewModel], and global UI effects in [RpsApp].
 */
object MatchSessionMonitor {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    private val _queueJoinedAtMs = MutableStateFlow<Long?>(null)
    val queueJoinedAtMs: StateFlow<Long?> = _queueJoinedAtMs.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var userListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null

    fun ensureStarted() {
        if (authListener != null) return
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            attachForUser(firebaseAuth.currentUser?.uid)
        }
        authListener = listener
        auth.addAuthStateListener(listener)
        attachForUser(auth.currentUser?.uid)
    }

    private fun attachForUser(uid: String?) {
        clearFirestoreListeners()
        _activeMatch.value = null
        _queueJoinedAtMs.value = null
        if (uid == null) return

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

                matchListener = firestore.collection("matches").document(matchId)
                    .addSnapshotListener { matchSnapshot, matchError ->
                        if (matchError != null) {
                            _activeMatch.value = null
                            return@addSnapshotListener
                        }
                        _activeMatch.value = matchSnapshot?.toMatch(matchId)
                    }
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
}
