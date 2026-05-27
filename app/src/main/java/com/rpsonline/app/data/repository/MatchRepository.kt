package com.rpsonline.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchEndReason
import com.rpsonline.app.data.model.MatchResolution
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.RoundEndReason
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.domain.DisplayNames
import com.rpsonline.app.domain.GameRules
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.data.model.RoundResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class MatchRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    companion object {
        /** Default per-side cap for time-bounded match queries (see [getRecentMatchesForUserSince]). */
        const val DEFAULT_SINCE_MATCH_LIMIT = 200
    }

    private val uid: String
        get() = auth.currentUser?.uid ?: error("Not signed in")

    /**
     * Join matchmaking via Firestore (not Callable). Writes queue/{uid}; Cloud Function pairs players.
     */
    suspend fun joinQueue(matchModes: Set<MatchMode>): String? {
        require(matchModes.isNotEmpty()) { "At least one match mode must be selected" }
        val userId = uid
        val userSnap = firestore.collection("users").document(userId).get().await()
        if (!userSnap.exists()) {
            throw IllegalStateException("User profile missing. Sign out and sign in again.")
        }

        val activeMatchId = userSnap.getString("activeMatchId")
        if (!activeMatchId.isNullOrBlank()) {
            val match = getMatch(activeMatchId)
            if (match?.status == MatchStatus.ACTIVE) {
                return activeMatchId
            }
        }

        val elo = userSnap.getLong("elo")?.toInt() ?: 1000
        val displayName = DisplayNames.resolve(userSnap.getString("displayName"), userId)

        firestore.collection("queue").document(userId).set(
            mapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "clientJoinedAt" to System.currentTimeMillis(),
                "elo" to elo,
                "displayName" to displayName,
                "matchModes" to matchModes.map { it.name },
            ),
        ).await()

        return null
    }

    suspend fun leaveQueue() {
        firestore.collection("queue").document(uid).delete().await()
    }

    fun observeQueue(): Flow<Long?> {
        MatchSessionMonitor.ensureStarted()
        return MatchSessionMonitor.queueJoinedAtMs
    }

    suspend fun requestRoundTimeout(matchId: String, roundNumber: Int) {
        // Auto-id doc: rules only allow create (not update). Reusing uid doc breaks tie replays.
        firestore.collection("matches")
            .document(matchId)
            .collection("rounds")
            .document(roundNumber.toString())
            .collection("timeoutRequests")
            .add(
                mapOf(
                    "userId" to uid,
                    "requestedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    suspend fun submitMove(matchId: String, move: Move, roundNumber: Int) {
        firestore.collection("matches")
            .document(matchId)
            .collection("rounds")
            .document(roundNumber.toString())
            .collection("choices")
            .document(uid)
            .set(
                mapOf(
                    "choice" to move.name,
                    "submittedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    fun observeMatch(matchId: String): Flow<Match?> = callbackFlow {
        val listener = firestore.collection("matches")
            .document(matchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toMatch(matchId))
            }
        awaitClose { listener.remove() }
    }

    fun observeActiveMatch(): Flow<Match?> {
        MatchSessionMonitor.ensureStarted()
        return MatchSessionMonitor.activeMatch
    }

    suspend fun getMatch(matchId: String): Match? {
        val snapshot = firestore.collection("matches").document(matchId).get().await()
        return if (snapshot.exists()) snapshot.toMatch(matchId) else null
    }

    suspend fun getRecentMatchesForUser(userId: String, limit: Int = 10): List<Match> {
        val perSide = limit.coerceAtLeast(1)
        val asPlayer1 = firestore.collection("matches")
            .whereEqualTo("player1", userId)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()
        val asPlayer2 = firestore.collection("matches")
            .whereEqualTo("player2", userId)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()

        return mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
    }

    suspend fun getRecentMatchesForUserSince(
        userId: String,
        sinceMs: Long,
        limit: Int = MatchRepository.DEFAULT_SINCE_MATCH_LIMIT,
    ): List<Match> {
        val since = Timestamp(Date(sinceMs))
        val perSide = limit.coerceAtLeast(1)
        val asPlayer1 = firestore.collection("matches")
            .whereEqualTo("player1", userId)
            .whereGreaterThanOrEqualTo("lastActivityAt", since)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()
        val asPlayer2 = firestore.collection("matches")
            .whereEqualTo("player2", userId)
            .whereGreaterThanOrEqualTo("lastActivityAt", since)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()

        return mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
    }

    /** Matches between [userId] and [opponentId], newest first. */
    suspend fun getSharedMatchesBetween(
        userId: String,
        opponentId: String,
        limit: Int = 10,
    ): List<Match> {
        val perSide = limit.coerceAtLeast(1)
        val asPlayer1 = firestore.collection("matches")
            .whereEqualTo("player1", userId)
            .whereEqualTo("player2", opponentId)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()
        val asPlayer2 = firestore.collection("matches")
            .whereEqualTo("player1", opponentId)
            .whereEqualTo("player2", userId)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()

        return mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
    }

    suspend fun getSharedMatchesBetweenSince(
        userId: String,
        opponentId: String,
        sinceMs: Long,
        limit: Int = DEFAULT_SINCE_MATCH_LIMIT,
    ): List<Match> {
        val since = Timestamp(Date(sinceMs))
        val perSide = limit.coerceAtLeast(1)
        val asPlayer1 = firestore.collection("matches")
            .whereEqualTo("player1", userId)
            .whereEqualTo("player2", opponentId)
            .whereGreaterThanOrEqualTo("lastActivityAt", since)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()
        val asPlayer2 = firestore.collection("matches")
            .whereEqualTo("player1", opponentId)
            .whereEqualTo("player2", userId)
            .whereGreaterThanOrEqualTo("lastActivityAt", since)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(perSide.toLong())
            .get()
            .await()

        return mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
    }

    /** Matches the signed-in [viewerId] can read that also involve [opponentId]. */
    suspend fun getRecentSharedMatches(
        viewerId: String,
        opponentId: String,
        limit: Int = 10,
    ): List<Match> = getSharedMatchesBetween(
        userId = viewerId,
        opponentId = opponentId,
        limit = limit,
    )

    suspend fun getRecentSharedMatchesSince(
        viewerId: String,
        opponentId: String,
        sinceMs: Long,
        limit: Int = MatchRepository.DEFAULT_SINCE_MATCH_LIMIT,
    ): List<Match> = getSharedMatchesBetweenSince(
        userId = viewerId,
        opponentId = opponentId,
        sinceMs = sinceMs,
        limit = limit,
    )
}

private fun mergeRecentMatches(documents: List<DocumentSnapshot>, limit: Int): List<Match> =
    documents
        .map { it.toMatch(it.id) }
        .filter { it.status == MatchStatus.COMPLETED || it.status == MatchStatus.ABANDONED }
        .distinctBy { it.id }
        .sortedByDescending { it.lastActivityAt }
        .take(limit)

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toMatch(id: String): Match {
    val roundsData = get("rounds") as? List<Map<String, Any?>> ?: emptyList()
    val rounds = roundsData.map { map ->
        RoundResult(
            roundNumber = (map["roundNumber"] as? Number)?.toInt() ?: 0,
            player1Choice = map["player1Choice"] as? String,
            player2Choice = map["player2Choice"] as? String,
            winner = map["winner"] as? String,
            endReason = RoundEndReason.fromString(map["endReason"] as? String),
            resolvedAt = (map["resolvedAt"] as? Timestamp)?.toDate()?.time,
            startedAt = (map["startedAt"] as? Timestamp)?.toDate()?.time,
            deadline = (map["deadline"] as? Timestamp)?.toDate()?.time,
            player1MoveMs = (map["player1MoveMs"] as? Number)?.toInt(),
            player2MoveMs = (map["player2MoveMs"] as? Number)?.toInt(),
        )
    }

    return Match(
        id = id,
        player1 = getString("player1") ?: "",
        player2 = getString("player2") ?: "",
        player1Name = getString("player1Name") ?: "Player 1",
        player2Name = getString("player2Name") ?: "Player 2",
        matchMode = MatchMode.fromString(getString("matchMode")),
        status = MatchStatus.fromString(getString("status")),
        currentRound = getLong("currentRound")?.toInt() ?: 1,
        player1Wins = getLong("player1Wins")?.toInt() ?: 0,
        player2Wins = getLong("player2Wins")?.toInt() ?: 0,
        player1MoveTimeMs = getLong("player1MoveTimeMs") ?: 0L,
        player2MoveTimeMs = getLong("player2MoveTimeMs") ?: 0L,
        player1MoveCount = getLong("player1MoveCount")?.toInt() ?: 0,
        player2MoveCount = getLong("player2MoveCount")?.toInt() ?: 0,
        player1ClockMs = getLong("player1ClockMs") ?: GameRules.INITIAL_CLOCK_MS,
        player2ClockMs = getLong("player2ClockMs") ?: GameRules.INITIAL_CLOCK_MS,
        clocksUpdatedAt = getTimestamp("clocksUpdatedAt")?.toDate()?.time ?: 0L,
        rounds = rounds,
        winnerId = getString("winnerId"),
        resolution = MatchResolution.fromString(getString("resolution")),
        endReason = MatchEndReason.fromString(getString("endReason")),
        player1EloDelta = getIntField("player1EloDelta"),
        player2EloDelta = getIntField("player2EloDelta"),
        player1Elo = getIntField("player1Elo"),
        player2Elo = getIntField("player2Elo"),
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        lastActivityAt = getTimestamp("lastActivityAt")?.toDate()?.time ?: 0L,
    )
}
