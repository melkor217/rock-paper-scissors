package com.rpsonline.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.rpsonline.app.BuildConfig
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class MatchRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    companion object {
        /** Default per-side cap for time-bounded match queries (see [getRecentMatchesForUserSince]). */
        const val DEFAULT_SINCE_MATCH_LIMIT = 200

        /**
         * Time-to-live for concluded match cache entries.
         *
         * The cache is in-memory and only stores matches that are already concluded
         * (COMPLETED or ABANDONED). Entries older than this will be refreshed from Firestore.
         */
        private const val CONCLUDED_MATCH_CACHE_TTL_MS = 2 * 60 * 1000L

        private const val PREFS_NAME = "concluded_match_cache"
        private const val PREF_KEY_VERSION_CODE = "version_code"

        /**
         * Simple in-memory cache for concluded match queries.
         *
         * Keys reflect the query type and parameters (user ids, since timestamp, limit).
         * The cache is also scoped to the current app version; if the version changes
         * (i.e., the app is updated), all cached entries are discarded.
         */
        private val concludedMatchCache =
            mutableMapOf<String, ConcludedMatchCacheEntry>()

        private var cacheVersionCode: Int? = null

        private val prefs: SharedPreferences
            get() = FirebaseApp.getInstance().applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun cacheKeyRecentForUser(userId: String, limit: Int): String =
            "recent_user:$userId:$limit"

        private fun cacheKeyRecentForUserSince(
            userId: String,
            sinceMs: Long,
            limit: Int,
        ): String = "recent_user_since:$userId:$sinceMs:$limit"

        private fun cacheKeySharedBetween(
            userId: String,
            opponentId: String,
            limit: Int,
        ): String = "shared_between:$userId:$opponentId:$limit"

        private fun cacheKeySharedBetweenSince(
            userId: String,
            opponentId: String,
            sinceMs: Long,
            limit: Int,
        ): String = "shared_between_since:$userId:$opponentId:$sinceMs:$limit"

        private fun nowMs(): Long = System.currentTimeMillis()

        private fun getCachedMatches(key: String): List<Match>? {
            synchronized(concludedMatchCache) {
                ensureCacheVersion()

                val now = nowMs()
                val inMemory = concludedMatchCache[key]
                val entry = inMemory ?: readCacheEntryFromPrefs(key)
                if (entry == null) return null

                return if (now - entry.createdAtMs <= CONCLUDED_MATCH_CACHE_TTL_MS) {
                    concludedMatchCache[key] = entry
                    entry.matches
                } else {
                    concludedMatchCache.remove(key)
                    prefs.edit().remove(key).apply()
                    null
                }
            }
        }

        private fun putCachedMatches(key: String, matches: List<Match>) {
            synchronized(concludedMatchCache) {
                ensureCacheVersion()
                val entry = ConcludedMatchCacheEntry(
                    createdAtMs = nowMs(),
                    matches = matches,
                )
                concludedMatchCache[key] = entry
                writeCacheEntryToPrefs(key, entry)
            }
        }

        private fun ensureCacheVersion() {
            val currentVersionCode = BuildConfig.VERSION_CODE
            if (cacheVersionCode == currentVersionCode) return

            val storedVersion = prefs.getInt(PREF_KEY_VERSION_CODE, -1)
            if (storedVersion != currentVersionCode) {
                prefs.edit()
                    .clear()
                    .putInt(PREF_KEY_VERSION_CODE, currentVersionCode)
                    .apply()
            }
            concludedMatchCache.clear()
            cacheVersionCode = currentVersionCode
        }

        private fun writeCacheEntryToPrefs(key: String, entry: ConcludedMatchCacheEntry) {
            val jsonArray = JSONArray()
            entry.matches.forEach { match ->
                jsonArray.put(match.toJson())
            }
            val combined = "${entry.createdAtMs}|${jsonArray}"
            prefs.edit().putString(key, combined).apply()
        }

        private fun readCacheEntryFromPrefs(key: String): ConcludedMatchCacheEntry? {
            val stored = prefs.getString(key, null) ?: return null
            val separatorIndex = stored.indexOf('|')
            if (separatorIndex <= 0 || separatorIndex >= stored.length - 1) return null

            val createdAtMs = stored.substring(0, separatorIndex).toLongOrNull() ?: return null
            val jsonPayload = stored.substring(separatorIndex + 1)
            val matches: List<Match> =
                try {
                    val array = JSONArray(jsonPayload)
                    buildList<Match> {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            add(obj.toMatch())
                        }
                    }
                } catch (_: Exception) {
                    return null
                }
            if (matches.isEmpty()) return null
            return ConcludedMatchCacheEntry(
                createdAtMs = createdAtMs,
                matches = matches,
            )
        }
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
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "clientJoinedAt" to System.currentTimeMillis(),
                "elo" to elo,
                "displayName" to displayName,
                "matchModes" to matchModes.map { it.name },
            ),
        ).await()

        return null
    }

    /**
     * Keeps the queue entry alive while the user is actively waiting for a match.
     * Failures are treated as transient; cleanup of truly stale entries is server-driven.
     */
    suspend fun sendQueueHeartbeat(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return runCatching {
            awaitFirestoreAuth()
            firestore.collection("queue").document(userId)
                .update(mapOf("lastHeartbeatAt" to FieldValue.serverTimestamp()))
                .await()
        }.isSuccess
    }

    suspend fun leaveQueue() {
        firestore.collection("queue").document(uid).delete().await()
    }

    fun leaveQueueBestEffort(userId: String) {
        firestore.collection("queue").document(userId).deleteBestEffort()
    }

    fun observeQueue(): Flow<Long?> {
        MatchSessionMonitor.ensureStarted()
        return MatchSessionMonitor.queueJoinedAtMs
    }

    suspend fun getQueueJoinedAtMs(): Long? {
        val snap = firestore.collection("queue").document(uid).get().await()
        if (!snap.exists()) return null
        return snap.getTimestamp("joinedAt")?.toDate()?.time
            ?: snap.getLong("clientJoinedAt")
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

    /** Fresh match state after resume; bypasses stale local cache. */
    suspend fun getMatchFromServer(matchId: String): Match? {
        awaitFirestoreAuth()
        val snapshot = firestore.collection("matches")
            .document(matchId)
            .get(Source.SERVER)
            .await()
        return if (snapshot.exists()) snapshot.toMatch(matchId) else null
    }

    suspend fun getRecentMatchesForUser(userId: String, limit: Int = 10): List<Match> {
        val cacheKey = cacheKeyRecentForUser(userId, limit)
        getCachedMatches(cacheKey)?.let { return it }

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

        val merged = mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
        putCachedMatches(cacheKey, merged)
        return merged
    }

    suspend fun getRecentMatchesForUserSince(
        userId: String,
        sinceMs: Long,
        limit: Int = MatchRepository.DEFAULT_SINCE_MATCH_LIMIT,
    ): List<Match> {
        val cacheKey = cacheKeyRecentForUserSince(userId, sinceMs, limit)
        getCachedMatches(cacheKey)?.let { return it }

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

        val merged = mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
        putCachedMatches(cacheKey, merged)
        return merged
    }

    /** Matches between [userId] and [opponentId], newest first. */
    suspend fun getSharedMatchesBetween(
        userId: String,
        opponentId: String,
        limit: Int = 10,
    ): List<Match> {
        val cacheKey = cacheKeySharedBetween(userId, opponentId, limit)
        getCachedMatches(cacheKey)?.let { return it }

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

        val merged = mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
        putCachedMatches(cacheKey, merged)
        return merged
    }

    suspend fun getSharedMatchesBetweenSince(
        userId: String,
        opponentId: String,
        sinceMs: Long,
        limit: Int = DEFAULT_SINCE_MATCH_LIMIT,
    ): List<Match> {
        val cacheKey = cacheKeySharedBetweenSince(userId, opponentId, sinceMs, limit)
        getCachedMatches(cacheKey)?.let { return it }

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

        val merged = mergeRecentMatches(asPlayer1.documents + asPlayer2.documents, limit)
        putCachedMatches(cacheKey, merged)
        return merged
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

private fun Match.toJson(): JSONObject {
    val obj = JSONObject()
    obj.put("id", id)
    obj.put("player1", player1)
    obj.put("player2", player2)
    obj.put("player1Name", player1Name)
    obj.put("player2Name", player2Name)
    obj.put("matchMode", matchMode.name)
    obj.put("status", status.name)
    obj.put("currentRound", currentRound)
    obj.put("player1Wins", player1Wins)
    obj.put("player2Wins", player2Wins)
    obj.put("player1MoveTimeMs", player1MoveTimeMs)
    obj.put("player2MoveTimeMs", player2MoveTimeMs)
    obj.put("player1MoveCount", player1MoveCount)
    obj.put("player2MoveCount", player2MoveCount)
    obj.put("player1ClockMs", player1ClockMs)
    obj.put("player2ClockMs", player2ClockMs)
    obj.put("clocksUpdatedAt", clocksUpdatedAt)
    obj.put("winnerId", winnerId)
    obj.put("resolution", resolution?.name)
    obj.put("endReason", endReason?.name)
    obj.put("player1EloDelta", player1EloDelta)
    obj.put("player2EloDelta", player2EloDelta)
    obj.put("player1Elo", player1Elo)
    obj.put("player2Elo", player2Elo)
    obj.put("createdAt", createdAt)
    obj.put("lastActivityAt", lastActivityAt)

    val roundsArray = JSONArray()
    rounds.forEach { round ->
        roundsArray.put(round.toJson())
    }
    obj.put("rounds", roundsArray)

    return obj
}

private fun JSONObject.toMatch(): Match {
    val roundsArray = optJSONArray("rounds") ?: JSONArray()
    val rounds = buildList {
        for (i in 0 until roundsArray.length()) {
            val obj = roundsArray.optJSONObject(i) ?: continue
            add(obj.toRoundResult())
        }
    }

    return Match(
        id = optString("id", ""),
        player1 = optString("player1", ""),
        player2 = optString("player2", ""),
        player1Name = optString("player1Name", ""),
        player2Name = optString("player2Name", ""),
        matchMode = MatchMode.fromString(optNullableString("matchMode")),
        status = MatchStatus.fromString(optNullableString("status")),
        currentRound = optInt("currentRound", 1),
        player1Wins = optInt("player1Wins", 0),
        player2Wins = optInt("player2Wins", 0),
        player1MoveTimeMs = optLong("player1MoveTimeMs", 0L),
        player2MoveTimeMs = optLong("player2MoveTimeMs", 0L),
        player1MoveCount = optInt("player1MoveCount", 0),
        player2MoveCount = optInt("player2MoveCount", 0),
        player1ClockMs = optLong("player1ClockMs", GameRules.INITIAL_CLOCK_MS),
        player2ClockMs = optLong("player2ClockMs", GameRules.INITIAL_CLOCK_MS),
        clocksUpdatedAt = optLong("clocksUpdatedAt", 0L),
        rounds = rounds,
        winnerId = optNullableString("winnerId"),
        resolution = MatchResolution.fromString(optNullableString("resolution")),
        endReason = MatchEndReason.fromString(optNullableString("endReason")),
        player1EloDelta = optNullableInt("player1EloDelta"),
        player2EloDelta = optNullableInt("player2EloDelta"),
        player1Elo = optNullableInt("player1Elo"),
        player2Elo = optNullableInt("player2Elo"),
        createdAt = optLong("createdAt", 0L),
        lastActivityAt = optLong("lastActivityAt", 0L),
    )
}

private fun RoundResult.toJson(): JSONObject {
    val obj = JSONObject()
    obj.put("roundNumber", roundNumber)
    obj.put("player1Choice", player1Choice)
    obj.put("player2Choice", player2Choice)
    obj.put("winner", winner)
    obj.put("endReason", endReason?.name)
    obj.put("resolvedAt", resolvedAt)
    obj.put("startedAt", startedAt)
    obj.put("deadline", deadline)
    obj.put("player1MoveMs", player1MoveMs)
    obj.put("player2MoveMs", player2MoveMs)
    return obj
}

private fun JSONObject.toRoundResult(): RoundResult =
    RoundResult(
        roundNumber = optInt("roundNumber", 0),
        player1Choice = optNullableString("player1Choice"),
        player2Choice = optNullableString("player2Choice"),
        winner = optNullableString("winner"),
        endReason = RoundEndReason.fromString(optNullableString("endReason")),
        resolvedAt = optNullableLong("resolvedAt"),
        startedAt = optNullableLong("startedAt"),
        deadline = optNullableLong("deadline"),
        player1MoveMs = optNullableInt("player1MoveMs"),
        player2MoveMs = optNullableInt("player2MoveMs"),
    )

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private data class ConcludedMatchCacheEntry(
    val createdAtMs: Long,
    val matches: List<Match>,
)
