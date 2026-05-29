package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.MatchMode
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal object MatchmakingFunctions {
    private const val REGION = "europe-west1"
    private const val JOIN_CALLABLE = "joinMatchmakingQueue"
    private const val CALL_TIMEOUT_MS = 22_000L

    suspend fun joinQueue(matchModes: Set<MatchMode>, profile: UserProfile): Long {
        require(matchModes.isNotEmpty())
        awaitFirestoreAuth(forceRefresh = true)
        val functions = FirebaseFunctions.getInstance(FirebaseApp.getInstance(), REGION)
        val payload = hashMapOf(
            "matchModes" to matchModes.map { it.name },
            "displayName" to profile.displayName,
            "elo" to profile.elo,
        )
        val result = withTimeout(CALL_TIMEOUT_MS) {
            functions.getHttpsCallable(JOIN_CALLABLE).call(payload).await()
        }
        @Suppress("UNCHECKED_CAST")
        val body = result.getData() as? Map<String, Any?>
            ?: throw IllegalStateException("Invalid matchmaking response")
        return (body["clientJoinedAtMs"] as? Number)?.toLong()
            ?: System.currentTimeMillis()
    }

    fun toJoinErrorMessage(error: Throwable): String? {
        val functionsError = error as? FirebaseFunctionsException ?: error.cause as? FirebaseFunctionsException
        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Sign in expired. Sign out and sign in again."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "Could not join matchmaking (permission denied)."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            ->
                "Could not reach the matchmaking server. Check your connection and try again."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message ?: "Invalid matchmaking request."
            else -> null
        }
    }
}
