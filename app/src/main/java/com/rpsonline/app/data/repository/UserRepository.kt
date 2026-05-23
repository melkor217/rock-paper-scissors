package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun getUserProfile(uid: String): UserProfile? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        return UserProfile(
            uid = uid,
            displayName = snapshot.getString("displayName") ?: "Player",
            photoUrl = snapshot.getString("photoUrl"),
            elo = snapshot.getLong("elo")?.toInt() ?: 1000,
            wins = snapshot.getLong("wins")?.toInt() ?: 0,
            losses = snapshot.getLong("losses")?.toInt() ?: 0,
            activeMatchId = snapshot.getString("activeMatchId"),
        )
    }

    suspend fun getLeaderboard(limit: Long = 50): List<LeaderboardEntry> {
        val snapshot = firestore.collection("users")
            .orderBy("elo", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.map { doc -> doc.toLeaderboardEntry() }
    }

    /** 1-based rank by Elo (tied Elo shares the same rank). */
    suspend fun getUserRank(uid: String): Int? {
        val profile = getUserProfile(uid) ?: return null
        val playersAbove = firestore.collection("users")
            .whereGreaterThan("elo", profile.elo)
            .get()
            .await()
            .size()
        return playersAbove + 1
    }

    suspend fun getLeaderboardEntry(uid: String): LeaderboardEntry? =
        getUserProfile(uid)?.toLeaderboardEntry()

    private fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry =
        LeaderboardEntry(
            uid = id,
            displayName = getString("displayName") ?: "Player",
            elo = getLong("elo")?.toInt() ?: 1000,
            wins = getLong("wins")?.toInt() ?: 0,
            losses = getLong("losses")?.toInt() ?: 0,
        )

    private fun UserProfile.toLeaderboardEntry(): LeaderboardEntry =
        LeaderboardEntry(
            uid = uid,
            displayName = displayName,
            elo = elo,
            wins = wins,
            losses = losses,
        )
}
