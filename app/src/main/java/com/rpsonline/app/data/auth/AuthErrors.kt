package com.rpsonline.app.data.auth

import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun Throwable.toAuthMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
    is FirebaseAuthInvalidUserException -> "No account found for this email"
    is FirebaseAuthUserCollisionException -> "An account already exists with this email"
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Invalid email address"
        "ERROR_USER_DISABLED" -> "This account has been disabled"
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again later"
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "This sign-in method is not enabled in Firebase Console"
        else -> message ?: "Authentication failed"
    }
    else -> message ?: "Authentication failed"
}

fun GetCredentialException.toGoogleSignInMessage(): String {
    val detail = message.orEmpty()
    return when {
        detail.contains("No credentials", ignoreCase = true) ||
            detail.contains("NoCredential", ignoreCase = true) ->
            "Google Sign-In is not configured for this app build. In Firebase Console, " +
                "add your release keystore SHA-1 under Project settings → Your apps → " +
                "Android → Add fingerprint, wait a few minutes, then try again."
        detail.contains("28433", ignoreCase = true) ||
            detail.contains("Cannot find a matching credential", ignoreCase = true) ->
            "Google Sign-In could not access saved credentials on this device. " +
                "Try again after setting a screen lock, or use email/guest sign-in."
        else -> detail.ifBlank { "Sign-in cancelled" }
    }
}
