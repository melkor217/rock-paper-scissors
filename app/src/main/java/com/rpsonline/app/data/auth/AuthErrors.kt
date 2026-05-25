package com.rpsonline.app.data.auth

import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

private fun Throwable.isNetworkError(): Boolean {
    if (this is FirebaseAuthException && errorCode == "ERROR_NETWORK_REQUEST_FAILED") return true
    val message = message.orEmpty()
  return message.contains("network", ignoreCase = true) ||
      message.contains("unreachable", ignoreCase = true) ||
      message.contains("timeout", ignoreCase = true) ||
      message.contains("Unable to resolve host", ignoreCase = true)
}

fun Throwable.toAuthMessage(): String = when {
    isNetworkError() -> "No internet connection. Check your network and try again."
    this is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
    this is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
    this is FirebaseAuthInvalidUserException -> "No account found for this email"
    this is FirebaseAuthUserCollisionException -> "An account already exists with this email"
    this is FirebaseAuthException -> when (errorCode) {
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
        detail.contains("network", ignoreCase = true) ||
            detail.contains("unreachable", ignoreCase = true) ||
            detail.contains("timeout", ignoreCase = true) ->
            "No internet connection. Check your network and try again."
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
