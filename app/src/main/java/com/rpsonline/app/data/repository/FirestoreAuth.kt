package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun awaitFirestoreAuth(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val user = auth.currentUser ?: return
    // Use cached token so Firestore works offline; forcing refresh hangs in airplane mode.
    withTimeoutOrNull(5_000) {
        user.getIdToken(false).await()
    }
}
