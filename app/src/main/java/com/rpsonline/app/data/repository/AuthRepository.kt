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
import com.google.firebase.firestore.Source
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = appFirestore(),
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
        discardIncompleteGuestProfile()
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
        awaitFirestoreAuth()
        val docRef = firestore.collection("users").document(uid)
        val snapshot = loadUserSnapshot(docRef)
        if (snapshot != null && snapshot.exists()) {
            if (!snapshot.isCompleteUserProfile()) {
                auth.signOut()
                error("Guest profile was incomplete. Tap Continue as guest again.")
            }
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
            buildMap {
                put("displayName", profile.displayName)
                profile.photoUrl?.let { put("photoUrl", it) }
                put("elo", profile.elo)
                put("wins", profile.wins)
                put("losses", profile.losses)
                put("draws", profile.draws)
                put("roundsWon", profile.roundsWon)
                put("roundsLost", profile.roundsLost)
                put("roundsDraw", profile.roundsDraw)
                put("moveTimeMs", profile.moveTimeMs)
                put("moveCount", profile.moveCount)
                put("throwsRock", profile.throwsRock)
                put("throwsPaper", profile.throwsPaper)
                put("throwsScissors", profile.throwsScissors)
                put("createdAt", now)
                put("lastSeen", now)
            },
        ).await()
        return profile
    }

    suspend fun loadCurrentUserProfile(): UserProfile? = runCatching {
        awaitFirestoreAuth()
        val uid = currentUserId ?: return@runCatching null
        val docRef = firestore.collection("users").document(uid)
        val snapshot = loadUserSnapshot(docRef) ?: return@runCatching null
        if (!snapshot.exists() || !snapshot.isCompleteUserProfile()) return@runCatching null
        normalizeStoredProfile(docRef, uid, snapshot.getString("displayName"), snapshot.toUserProfile(uid))
    }.getOrNull()

    fun fallbackProfile(user: FirebaseUser): UserProfile {
        val uid = user.uid
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: if (user.isAnonymous) DisplayNames.guestName(uid) else DisplayNames.DEFAULT
        return UserProfile(uid = uid, displayName = name, photoUrl = user.photoUrl?.toString())
    }

    private suspend fun loadUserSnapshot(
        docRef: com.google.firebase.firestore.DocumentReference,
    ): com.google.firebase.firestore.DocumentSnapshot? {
        val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        if (cached?.exists() == true) return cached
        return runCatching { docRef.get().await() }.getOrNull() ?: cached
    }

    private suspend fun discardIncompleteGuestProfile() {
        val uid = currentUserId ?: return
        if (auth.currentUser?.isAnonymous != true) return
        runCatching {
            awaitFirestoreAuth()
            val snapshot = loadUserSnapshot(firestore.collection("users").document(uid))
                ?: return@runCatching
            if (snapshot.exists() && !snapshot.isCompleteUserProfile()) {
                auth.signOut()
            }
        }
    }

    private suspend fun normalizeStoredProfile(
        docRef: com.google.firebase.firestore.DocumentReference,
        uid: String,
        storedName: String?,
        profile: UserProfile,
    ): UserProfile {
        val resolved = DisplayNames.resolve(storedName, uid)
        if (auth.currentUser?.isAnonymous == true && DisplayNames.isGeneric(storedName)) {
            docRef.updateBestEffort(mapOf("displayName" to resolved))
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

private fun com.google.firebase.firestore.DocumentSnapshot.isCompleteUserProfile(): Boolean =
    contains("displayName") &&
        contains("elo") &&
        contains("createdAt") &&
        contains("throwsRock") &&
        contains("throwsPaper") &&
        contains("throwsScissors")

private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(uid: String): UserProfile {
    return UserProfile(
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
}
