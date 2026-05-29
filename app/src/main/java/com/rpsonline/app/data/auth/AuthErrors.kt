package com.rpsonline.app.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.TimeoutCancellationException

private fun Throwable.isAppCheckError(): Boolean {
    val message = message.orEmpty()
    return message.contains("app check", ignoreCase = true) ||
        message.contains("App Check", ignoreCase = true) ||
        message.contains("attestation", ignoreCase = true) ||
        message.contains("Firebase App Check API", ignoreCase = true)
}

private fun Throwable.isFirestoreCacheMiss(): Boolean {
    val message = message.orEmpty()
    return message.contains("document from cache", ignoreCase = true) ||
        message.contains("from cache", ignoreCase = true) &&
        message.contains("may exist on the server", ignoreCase = true)
}

private fun Throwable.isNetworkError(): Boolean {
    if (this is FirebaseAuthException && errorCode == "ERROR_NETWORK_REQUEST_FAILED") return true
    val message = message.orEmpty()
    return message.contains("Unable to resolve host", ignoreCase = true) ||
        message.contains("A network error (such as timeout", ignoreCase = true) ||
        message.contains("NetworkError", ignoreCase = true)
}

fun Throwable.toAuthMessage(): String = when {
    this is TimeoutCancellationException ->
        "Sign-in timed out. Try again in a moment."
    isAppCheckError() ->
        "Firebase App Check rejected this request. In Firebase Console set App Check to Monitoring " +
            "(not Enforced) for sideload builds, or re-enable App Check in the app for Play Store."
    isFirestoreCacheMiss() ->
        "Still syncing your profile. Try Google sign-in again."
    isNetworkError() ->
        "Could not reach sign-in servers. Check your connection and that Anonymous sign-in is enabled in Firebase."
    this is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
    this is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
    this is FirebaseAuthInvalidUserException -> "No account found for this email"
    this is FirebaseAuthUserCollisionException -> "An account already exists with this email"
    this is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Invalid email address"
        "ERROR_USER_DISABLED" -> "This account has been disabled"
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again later"
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "Guest sign-in is disabled. In Firebase Console enable Authentication → Sign-in method → Anonymous."
        "ERROR_INTERNAL_ERROR" -> message?.takeIf { it.isNotBlank() }
            ?: "Authentication server error. Check App Check and sign-in methods in Firebase Console."
        else -> message?.takeIf { it.isNotBlank() } ?: "Authentication failed ($errorCode)"
    }
    else -> message?.takeIf { it.isNotBlank() } ?: "Authentication failed"
}

fun GetCredentialException.toGoogleSignInMessage(isDebugBuild: Boolean = false): String {
    when (this) {
        is GetCredentialCancellationException ->
            return "Google sign-in was cancelled."
        is NoCredentialException ->
            return noGoogleCredentialMessage(isDebugBuild)
    }
    val detail = message.orEmpty()
    return when {
        detail.contains("network", ignoreCase = true) ||
            detail.contains("unreachable", ignoreCase = true) ||
            detail.contains("timeout", ignoreCase = true) ->
            "Could not reach Google sign-in. Check your connection and try again."
        detail.contains("cancel", ignoreCase = true) ->
            "Google sign-in was cancelled."
        detail.contains("28433", ignoreCase = true) ||
            detail.contains("Cannot find a matching credential", ignoreCase = true) ->
            "Google Sign-In could not access credentials on this device. Add a Google account or use guest/email."
        detail.contains("No credentials", ignoreCase = true) ||
            detail.contains("NoCredential", ignoreCase = true) ->
            noGoogleCredentialMessage(isDebugBuild)
        else -> detail.ifBlank { "Google sign-in failed. Try again." }
    }
}

private fun noGoogleCredentialMessage(isDebugBuild: Boolean): String {
    return if (isDebugBuild) {
        "Google Sign-In could not start. Add a Google account on this device (Settings → Passwords & accounts), " +
            "use an emulator with Play Store, and ensure your debug SHA-1 is in Firebase → Project settings → " +
            "Android app → Fingerprints. Place google-services.json in app/ and rebuild."
    } else {
        "Google Sign-In could not start. Add a Google account on this device and try again. " +
            "If you installed from GitHub, register the release keystore SHA-1 in Firebase → Project settings → " +
            "Android app → Fingerprints (see scripts/ENABLE_AUTH.md)."
    }
}
