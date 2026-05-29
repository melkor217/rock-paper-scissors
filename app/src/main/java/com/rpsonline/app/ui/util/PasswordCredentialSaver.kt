package com.rpsonline.app.ui.util

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import kotlinx.coroutines.CancellationException

/**
 * Shows the system UI (e.g. Google Password Manager) to save email + password.
 * Requires an [Activity] context — Compose [androidx.compose.ui.platform.LocalContext]
 * from an Activity works.
 */
suspend fun offerSavePassword(
    context: Context,
    email: String,
    password: String,
) {
    val activity = context.findActivity() ?: return
    if (password.isEmpty()) return
    try {
        val request = CreatePasswordRequest(
            id = email,
            password = password,
        )
        CredentialManager.create(activity).createCredential(activity, request)
    } catch (_: CreateCredentialCancellationException) {
        // User dismissed save dialog
    } catch (_: CancellationException) {
        // Activity paused while the system save UI was open
    } catch (e: CreateCredentialException) {
        if (e.isBenignSaveCancellation()) return
        commitAutofillSave(activity)
    } catch (e: Exception) {
        if (e.isBenignSaveCancellation()) return
        commitAutofillSave(activity)
    }
}

private fun Throwable.isBenignSaveCancellation(): Boolean {
    if (this is CancellationException) return true
    val message = message.orEmpty()
    return message.contains("cancel", ignoreCase = true)
}
