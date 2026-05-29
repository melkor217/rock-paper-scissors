package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads from the local Firestore cache. When the document was never cached, the SDK throws
 * ("Failed to get document from cache") instead of returning an empty snapshot.
 */
internal suspend fun DocumentReference.getCacheSnapshotOrNull(
    timeoutMs: Long = 4_000,
): DocumentSnapshot? =
    withTimeoutOrNull(timeoutMs) {
        runCatching { get(Source.CACHE).await() }.getOrNull()
    }

internal suspend fun DocumentReference.getServerSnapshotOrNull(
    timeoutMs: Long = 8_000,
): DocumentSnapshot? =
    withTimeoutOrNull(timeoutMs) {
        runCatching { get(Source.SERVER).await() }.getOrNull()
    }

/** Full profile shape required for leaderboard/history features. */
internal fun DocumentSnapshot.isCompleteUserProfile(): Boolean =
    exists() &&
        contains("displayName") &&
        contains("elo") &&
        contains("createdAt") &&
        contains("throwsRock") &&
        contains("throwsPaper") &&
        contains("throwsScissors")

/** Minimum Firestore user fields needed to write queue/{uid}. */
internal fun DocumentSnapshot.hasMinimumQueueProfile(): Boolean =
    exists() && contains("displayName") && contains("elo")

internal fun DocumentSnapshot.isQueueReadyProfile(): Boolean =
    isCompleteUserProfile() || hasMinimumQueueProfile()

internal fun DocumentSnapshot.toUserProfile(uid: String): UserProfile =
    UserProfile(
        uid = uid,
        displayName = DisplayNames.resolve(getString("displayName"), uid),
        photoUrl = getString("photoUrl"),
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
        activeMatchId = getString("activeMatchId"),
    )
