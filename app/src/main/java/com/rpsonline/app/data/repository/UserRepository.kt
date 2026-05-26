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
        val snapshot = runCatching { ref.get(Source.SERVER).await() }.getOrNull()
            ?: runCatching { ref.get(Source.CACHE).await() }.getOrNull()
            ?: return null
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
            .filter { it.wins + it.losses + it.draws > 0 }
            .take(limit.toInt())
    }

    private fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry =
        LeaderboardEntry(
            uid = id,
            displayName = DisplayNames.resolve(getString("displayName"), id),
            elo = getIntField("elo") ?: 1000,
            wins = getIntField("wins") ?: 0,
            losses = getIntField("losses") ?: 0,
            draws = getIntField("draws") ?: 0,
            moveTimeMs = getLong("moveTimeMs") ?: 0L,
            moveCount = getIntField("moveCount") ?: 0,
            throwsRock = getIntField("throwsRock") ?: 0,
            throwsPaper = getIntField("throwsPaper") ?: 0,
            throwsScissors = getIntField("throwsScissors") ?: 0,
        )
}

private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile =
    UserProfile(
        uid = uid,
        displayName = DisplayNames.resolve(getString("displayName"), uid),
        photoUrl = getString("photoUrl"),
        elo = getIntField("elo") ?: 1000,
        wins = getIntField("wins") ?: 0,
        losses = getIntField("losses") ?: 0,
        draws = getIntField("draws") ?: 0,
        moveTimeMs = getLong("moveTimeMs") ?: 0L,
        moveCount = getIntField("moveCount") ?: 0,
        throwsRock = getIntField("throwsRock") ?: 0,
        throwsPaper = getIntField("throwsPaper") ?: 0,
        throwsScissors = getIntField("throwsScissors") ?: 0,
        activeMatchId = getString("activeMatchId"),
    )
