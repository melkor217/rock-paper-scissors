package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.rpsonline.app.data.model.Move
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal object GameFunctions {
    private const val SUBMIT_MOVE_CALLABLE = "submitMatchMove"
    private const val CALL_TIMEOUT_MS = 15_000L

    suspend fun submitMove(matchId: String, roundNumber: Int, move: Move) {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                awaitCallableAuth()
                val functions = FirebaseFunctions.getInstance(
                    FirebaseApp.getInstance(),
                    FIREBASE_FUNCTIONS_REGION,
                )
                val payload = hashMapOf(
                    "matchId" to matchId,
                    "roundNumber" to roundNumber,
                    "choice" to move.name,
                )
                withTimeout(CALL_TIMEOUT_MS) {
                    functions.getHttpsCallable(SUBMIT_MOVE_CALLABLE).call(payload).await()
                }
                return
            } catch (e: FirebaseFunctionsException) {
                lastError = e
                if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED && attempt == 0) {
                    delay(400)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt > 0) throw e
                delay(400)
            }
        }
        throw lastError ?: IllegalStateException("Could not submit move via server")
    }

    fun isRecoverableViaFirestore(error: Throwable): Boolean {
        val functionsError = error as? FirebaseFunctionsException ?: error.cause as? FirebaseFunctionsException
        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.NOT_FOUND,
            -> true
            else -> false
        }
    }

    fun toSubmitErrorMessage(error: Throwable): String? {
        if (isRecoverableViaFirestore(error)) return null
        val functionsError = error as? FirebaseFunctionsException ?: error.cause as? FirebaseFunctionsException
        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Could not verify your account with the game server. Sign out and sign in again."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "You cannot submit a move in this match."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                functionsError.message ?: "This round is no longer open."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message ?: "Invalid move."
            else -> null
        }
    }
}
