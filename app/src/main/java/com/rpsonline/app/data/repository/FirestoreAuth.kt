package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun awaitFirestoreAuth(
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
    forceRefresh: Boolean = false,
) {
    val user = auth.currentUser ?: return
    // After OAuth sign-in Firestore may still see the previous session until the token refreshes.
    withTimeoutOrNull(10_000) {
        user.getIdToken(forceRefresh).await()
    }
}
