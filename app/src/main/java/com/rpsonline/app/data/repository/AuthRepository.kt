package com.rpsonline.app.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(idToken: String): UserProfile {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("Google sign-in failed")
        return ensureUserProfile(user.uid, user.displayName, user.photoUrl?.toString())
    }

    suspend fun signInAnonymously(): UserProfile {
        val result = auth.signInAnonymously().await()
        val user = result.user ?: error("Guest sign-in failed")
        val guestName = DisplayNames.guestName(user.uid)
        return ensureUserProfile(user.uid, guestName, photoUrl = null)
    }

    suspend fun signInWithEmail(email: String, password: String): UserProfile {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Email sign-in failed")
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@').ifBlank { DisplayNames.DEFAULT }
        return ensureUserProfile(user.uid, name, user.photoUrl?.toString())
    }

    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): UserProfile {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Registration failed")
        val name = displayName.trim().ifBlank { email.substringBefore('@') }
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build(),
        ).await()
        return ensureUserProfile(user.uid, name, photoUrl = null)
    }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String?,
        photoUrl: String?,
    ): UserProfile {
        val docRef = firestore.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            return normalizeStoredProfile(docRef, uid, snapshot.getString("displayName"), snapshot.toUserProfile(uid))
        }

        val defaultName = when {
            auth.currentUser?.isAnonymous == true -> DisplayNames.guestName(uid)
            else -> displayName ?: DisplayNames.DEFAULT
        }
        val profile = UserProfile(
            uid = uid,
            displayName = defaultName,
            photoUrl = photoUrl,
        )
        val now = Timestamp.now()
        docRef.set(
            mapOf(
                "displayName" to profile.displayName,
                "photoUrl" to profile.photoUrl,
                "elo" to profile.elo,
                "wins" to profile.wins,
                "losses" to profile.losses,
                "throwsRock" to profile.throwsRock,
                "throwsPaper" to profile.throwsPaper,
                "throwsScissors" to profile.throwsScissors,
                "createdAt" to now,
                "lastSeen" to now,
            )
        ).await()
        return profile
    }

    suspend fun loadCurrentUserProfile(): UserProfile? {
        val uid = currentUserId ?: return null
        val docRef = firestore.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return null
        return normalizeStoredProfile(docRef, uid, snapshot.getString("displayName"), snapshot.toUserProfile(uid))
    }

    private suspend fun normalizeStoredProfile(
        docRef: com.google.firebase.firestore.DocumentReference,
        uid: String,
        storedName: String?,
        profile: UserProfile,
    ): UserProfile {
        val resolved = DisplayNames.resolve(storedName, uid)
        if (auth.currentUser?.isAnonymous == true && DisplayNames.isGeneric(storedName)) {
            docRef.update("displayName", resolved).await()
            return profile.copy(displayName = resolved)
        }
        return if (resolved != profile.displayName) profile.copy(displayName = resolved) else profile
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(
                ClearCredentialStateRequest(),
            )
        } catch (_: Exception) {
            // Google credential cache may already be clear
        }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(uid: String): UserProfile {
    return UserProfile(
        uid = uid,
        displayName = DisplayNames.resolve(getString("displayName"), uid),
        photoUrl = getString("photoUrl"),
        elo = getLong("elo")?.toInt() ?: 1000,
        wins = getLong("wins")?.toInt() ?: 0,
        losses = getLong("losses")?.toInt() ?: 0,
        throwsRock = getLong("throwsRock")?.toInt() ?: 0,
        throwsPaper = getLong("throwsPaper")?.toInt() ?: 0,
        throwsScissors = getLong("throwsScissors")?.toInt() ?: 0,
        activeMatchId = getString("activeMatchId"),
    )
}
