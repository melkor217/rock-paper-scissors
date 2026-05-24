package com.rpsonline.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

internal suspend fun awaitFirestoreAuth(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val user = auth.currentUser ?: return
    user.getIdToken(true).await()
}
