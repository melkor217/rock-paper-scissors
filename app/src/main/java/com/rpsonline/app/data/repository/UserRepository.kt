package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
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
        val ref = firestore.collection("users").document(uid)
        val cached = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        if (cached?.exists() == true) return cached.toUserProfile(uid)
        val snapshot = runCatching { ref.get().await() }.getOrNull() ?: return null
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
            .limit(limit * 4)
            .get()
            .await()

        return snapshot.documents
            .map { doc -> doc.toLeaderboardEntry() }
            .filter { it.wins + it.losses > 0 }
            .take(limit.toInt())
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
