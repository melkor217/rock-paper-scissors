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
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    private companion object {
        // Query uses `elo` ordering, but we need extra candidates because we filter out users
        // with 0 games (wins + losses + draws == 0).
        private const val LEADERBOARD_OVERSAMPLE_FACTOR = 4
    }

    internal data class LeaderboardPage(
        val entries: List<LeaderboardEntry>,
        val nextCursor: DocumentSnapshot?,
        val hasMoreFromFirestore: Boolean,
        val pendingEntries: List<LeaderboardEntry> = emptyList(),
    ) {
        val hasMore: Boolean
            get() = pendingEntries.isNotEmpty() || hasMoreFromFirestore
    }

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

    suspend fun getLeaderboard(limit: Long = 50): List<LeaderboardEntry> =
        getLeaderboardPage(pageSize = limit).entries

    /**
     * Fetches a page of leaderboard entries ordered by `elo` descending.
     *
     * Supports cursor-based pagination via `startAfter(nextCursor)`.
     */
    internal suspend fun getLeaderboardPage(
        pageSize: Long,
        startAfter: DocumentSnapshot? = null,
    ): LeaderboardPage {
        require(pageSize > 0) { "pageSize must be > 0" }

        val queryLimit = pageSize * LEADERBOARD_OVERSAMPLE_FACTOR
        val entries = mutableListOf<LeaderboardEntry>()
        val pending = mutableListOf<LeaderboardEntry>()

        var firestoreCursor: DocumentSnapshot? = startAfter
        var hasMoreFromFirestore = true

        while (entries.size < pageSize && (pending.isNotEmpty() || hasMoreFromFirestore)) {
            if (pending.isEmpty()) {
                var query =
                    firestore.collection("users").orderBy("elo", Query.Direction.DESCENDING)
                if (firestoreCursor != null) query = query.startAfter(firestoreCursor)

                val snapshot = query.limit(queryLimit).get().await()
                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    hasMoreFromFirestore = false
                    break
                }

                firestoreCursor = docs.last()
                hasMoreFromFirestore = docs.size.toLong() == queryLimit

                pending += docs
                    .map { doc -> doc.toLeaderboardEntry() }
                    .filter { it.wins + it.losses + it.draws > 0 }
            }

            val remaining = (pageSize - entries.size).toInt()
            if (remaining <= 0) break

            val takeCount = minOf(remaining, pending.size)
            if (takeCount == 0) {
                if (!hasMoreFromFirestore) break
                continue
            }

            entries += pending.subList(0, takeCount)
            repeat(takeCount) { pending.removeAt(0) }
        }

        return LeaderboardPage(
            entries = entries,
            nextCursor = firestoreCursor,
            hasMoreFromFirestore = hasMoreFromFirestore,
            pendingEntries = pending.toList(),
        )
    }

    private fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry =
        LeaderboardEntry(
            uid = id,
            displayName = DisplayNames.resolve(getString("displayName"), id),
            elo = getIntField("elo") ?: 1000,
            wins = getIntField("wins") ?: 0,
            losses = getIntField("losses") ?: 0,
            draws = getIntField("draws") ?: 0,
            roundsWon = getIntField("roundsWon") ?: 0,
            roundsLost = getIntField("roundsLost") ?: 0,
            roundsDraw = getIntField("roundsDraw") ?: 0,
            moveTimeMs = getLong("moveTimeMs") ?: 0L,
            moveCount = getIntField("moveCount") ?: 0,
            throwsRock = getIntField("throwsRock") ?: 0,
            throwsPaper = getIntField("throwsPaper") ?: 0,
            throwsScissors = getIntField("throwsScissors") ?: 0,
        )
}
