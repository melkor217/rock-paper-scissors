package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.rpsonline.app.data.model.Move
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal object GameFunctions {
    private const val SUBMIT_MOVE_CALLABLE = "submitMatchMove"
    private const val CALL_TIMEOUT_MS = 15_000L

    suspend fun submitMove(matchId: String, roundNumber: Int, move: Move) {
        awaitFirestoreAuth(forceRefresh = true)
        val functions = FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FIREBASE_FUNCTIONS_REGION)
        val payload = hashMapOf(
            "matchId" to matchId,
            "roundNumber" to roundNumber,
            "choice" to move.name,
        )
        withTimeout(CALL_TIMEOUT_MS) {
            functions.getHttpsCallable(SUBMIT_MOVE_CALLABLE).call(payload).await()
        }
    }

    fun toSubmitErrorMessage(error: Throwable): String? {
        val functionsError = error as? FirebaseFunctionsException ?: error.cause as? FirebaseFunctionsException
        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Sign in expired. Sign out and sign in again."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "You cannot submit a move in this match."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                functionsError.message ?: "This round is no longer open."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            ->
                "Could not reach the game server. Check your connection and try again."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message ?: "Invalid move."
            else -> null
        }
    }
}
