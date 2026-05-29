package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal object PresenceFunctions {
    private const val TOUCH_CALLABLE = "touchPresence"
    private const val CALL_TIMEOUT_MS = 10_000L

    /**
     * Updates presence on the server. Returns server wall time (ms) when successful, else null.
     */
    suspend fun tryTouchPresence(): Long? {
        repeat(2) { attempt ->
            try {
                awaitCallableAuth()
                val functions = FirebaseFunctions.getInstance(
                    FirebaseApp.getInstance(),
                    FIREBASE_FUNCTIONS_REGION,
                )
                val result = withTimeout(CALL_TIMEOUT_MS) {
                    functions.getHttpsCallable(TOUCH_CALLABLE).call(emptyMap<String, Any>()).await()
                }
                @Suppress("UNCHECKED_CAST")
                val body = result.getData() as? Map<String, Any?> ?: return System.currentTimeMillis()
                return (body["serverTimeMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
            } catch (e: FirebaseFunctionsException) {
                if (e.code == FirebaseFunctionsException.Code.UNAUTHENTICATED && attempt == 0) {
                    delay(400)
                } else {
                    return null
                }
            } catch (e: Exception) {
                if (attempt > 0) return null
                delay(400)
            }
        }
        return null
    }
}
