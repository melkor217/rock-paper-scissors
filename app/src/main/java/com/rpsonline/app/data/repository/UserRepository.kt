package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun getUserProfile(uid: String): UserProfile? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        return snapshot.toUserProfile(uid)
    }

    /** Live updates when Cloud Functions increment throw counts, ELO, etc. */
    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toUserProfile(uid))
            }
        awaitClose { listener.remove() }
    }

    suspend fun getLeaderboard(limit: Long = 50): List<LeaderboardEntry> {
        val snapshot = firestore.collection("users")
            .orderBy("elo", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.map { doc -> doc.toLeaderboardEntry() }
    }

    private fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry =
        LeaderboardEntry(
            uid = id,
            displayName = DisplayNames.resolve(getString("displayName"), id),
            elo = getLong("elo")?.toInt() ?: 1000,
            wins = getLong("wins")?.toInt() ?: 0,
            losses = getLong("losses")?.toInt() ?: 0,
            throwsRock = getLong("throwsRock")?.toInt() ?: 0,
            throwsPaper = getLong("throwsPaper")?.toInt() ?: 0,
            throwsScissors = getLong("throwsScissors")?.toInt() ?: 0,
        )
}

private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile =
    UserProfile(
        uid = uid,
        displayName = DisplayNames.resolve(getString("displayName"), uid),
        photoUrl = getString("photoUrl"),
        elo = getLong("elo")?.toInt() ?: 1000,
        wins = getLong("wins")?.toInt() ?: 0,
        losses = getLong("losses")?.toInt() ?: 0,
        throwsRock = getLong("throwsRock")?.toInt() ?: 0,
        throwsPaper = getLong("throwsPaper")?.toInt() ?: 0,
        throwsScissors = getLong("throwsScissors")?.toInt() ?: 0,
        activeMatchId = getString("activeMatchId"),
    )
