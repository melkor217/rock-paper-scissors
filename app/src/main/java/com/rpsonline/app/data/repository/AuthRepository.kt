package com.rpsonline.app.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Timestamp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = appFirestore(),
) {
    private companion object {
        private const val TAG = "AuthRepository"
    }

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

    /** Firebase auth only — profile sync can finish after the UI navigates away. */
    suspend fun beginAnonymousSignIn(): FirebaseUser = withContext(Dispatchers.IO) {
        withTimeoutOrNull(2_000) { discardIncompleteGuestProfile() }
        try {
            val result = withTimeout(30_000) {
                auth.signInAnonymously().awaitTask()
            }
            result.user ?: error("Guest sign-in failed")
        } catch (e: TimeoutCancellationException) {
            // Auth may have completed even though the coroutine timed out waiting.
            auth.currentUser ?: throw e
        }
    }

    suspend fun signInAnonymously(): UserProfile {
        val user = beginAnonymousSignIn()
        return ensureUserProfile(
            uid = user.uid,
            displayName = DisplayNames.guestName(user.uid),
            photoUrl = null,
        )
    }

    fun signOutBestEffort() {
        runCatching { auth.signOut() }
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
                withTimeoutOrNull(8_000) { docRef.delete().await() }
            } else {
                return normalizeStoredProfile(
                    docRef,
                    uid,
                    snapshot.getString("displayName"),
                    snapshot.toUserProfile(uid),
                )
            }
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
        withTimeout(15_000) {
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
        }
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

    suspend fun isFirebaseAvailable(): Boolean {
        runCatching { appFirestore().enableNetwork().await() }
        return probeFirestoreReachability() || probeFirestoreReachabilityFromCache()
    }

    /**
     * App Check is required for Auth when enforcement is enabled, but it must not gate the launch
     * connectivity probe (emulators often lack a registered debug token until first Logcat run).
     */
    suspend fun isAppCheckTokenAvailable(): Boolean = probeAppCheckToken()

    /** When non-null, App Check is blocking Firebase Auth/Firestore. */
    suspend fun appCheckErrorMessageOrNull(): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(10_000) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "App Check token unavailable", e)
            buildAppCheckErrorMessage(e)
        }
    }

    private fun buildAppCheckErrorMessage(cause: Exception): String {
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
        return if (BuildConfig.DEBUG) {
            "App Check failed ($detail). On debug builds (emulator or phone): open Logcat, filter " +
                "DebugAppCheckProvider, copy the debug secret, add it in Firebase Console → App Check → " +
                "your Android app → Manage debug tokens, then cold-restart the app."
        } else {
            "App Check failed ($detail). Release builds need Play Integrity in Firebase Console → " +
                "App Check → register the Play Integrity provider for this app (and the app listed in " +
                "Google Play with the same package name). For local testing, install a debug build instead."
        }
    }

    private suspend fun probeAppCheckToken(): Boolean {
        return try {
            withTimeoutOrNull(8_000) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
                true
            } == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Unauthenticated clients cannot read [users], but a rules rejection still proves Firestore
     * is reachable. Avoid [Source.SERVER] here — it blocks for a long time when offline.
     */
    private suspend fun probeFirestoreReachability(): Boolean {
        return try {
            withTimeoutOrNull(10_000) {
                firestore.collection("users").limit(1).get().await()
                true
            } ?: false
        } catch (e: FirebaseFirestoreException) {
            interpretUsersProbeError(e)
        } catch (_: TimeoutCancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun probeFirestoreReachabilityFromCache(): Boolean {
        return try {
            withTimeoutOrNull(2_000) {
                firestore.collection("users").limit(1).get(Source.CACHE).await()
                true
            } ?: false
        } catch (e: FirebaseFirestoreException) {
            interpretUsersProbeError(e)
        } catch (_: Exception) {
            false
        }
    }

    private fun interpretUsersProbeError(error: FirebaseFirestoreException): Boolean =
        when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            -> true
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> false
            else -> true
        }

    private suspend fun loadUserSnapshot(
        docRef: com.google.firebase.firestore.DocumentReference,
    ): com.google.firebase.firestore.DocumentSnapshot? {
        val cached = withTimeoutOrNull(4_000) {
            docRef.get(Source.CACHE).await()
        }
        if (cached?.exists() == true) return cached
        val remote = withTimeoutOrNull(8_000) {
            docRef.get().await()
        }
        return remote ?: cached
    }

    /** Drops a broken guest Firestore doc without signing out (sign-out resets navigation). */
    private suspend fun discardIncompleteGuestProfile() {
        val uid = currentUserId ?: return
        if (auth.currentUser?.isAnonymous != true) return
        withTimeoutOrNull(3_000) {
            runCatching {
                awaitFirestoreAuth()
                val docRef = firestore.collection("users").document(uid)
                val cached = withTimeoutOrNull(1_000) {
                    docRef.get(Source.CACHE).await()
                }
                if (cached?.exists() == true && !cached.isCompleteUserProfile()) {
                    docRef.delete().await()
                    return@runCatching
                }
                val remote = withTimeoutOrNull(2_000) { docRef.get().await() }
                if (remote?.exists() == true && !remote.isCompleteUserProfile()) {
                    docRef.delete().await()
                }
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
        withTimeoutOrNull(2_000) {
            try {
                CredentialManager.create(context).clearCredentialState(
                    ClearCredentialStateRequest(),
                )
            } catch (_: Exception) {
                // Google credential cache may already be clear.
            }
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
