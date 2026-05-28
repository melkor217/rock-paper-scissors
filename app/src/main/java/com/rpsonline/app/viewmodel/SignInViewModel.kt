package com.rpsonline.app.viewmodel

import android.content.Context
import com.rpsonline.app.ui.util.offerSavePassword
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rpsonline.app.R
import com.rpsonline.app.data.auth.toAuthMessage
import com.rpsonline.app.data.auth.toGoogleSignInMessage
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

enum class EmailAuthMode {
    SIGN_IN,
    REGISTER,
}

data class SignInUiState(
    val isLoading: Boolean = false,
    val isRestoringSession: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val emailMode: EmailAuthMode = EmailAuthMode.SIGN_IN,
)

class SignInViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(isLoading = true, isRestoringSession = true, error = null)
                    }
                    try {
                        val profile = withTimeout(10_000) {
                            authRepository.loadCurrentUserProfile() ?: authRepository.ensureUserProfile(
                                uid = user.uid,
                                displayName = user.displayName,
                                photoUrl = user.photoUrl?.toString(),
                            )
                        }
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                isLoading = false,
                                isRestoringSession = false,
                                error = null,
                            )
                        }
                    } catch (e: TimeoutCancellationException) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRestoringSession = false,
                                error = "Unable to restore your profile right now. Check your connection and try again.",
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                error = e.toAuthMessage(),
                                isLoading = false,
                                isRestoringSession = false,
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            profile = null,
                            isLoading = false,
                            isRestoringSession = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value, error = null) }
    }

    fun setEmailMode(mode: EmailAuthMode) {
        _uiState.update { it.copy(emailMode = mode, error = null) }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRestoringSession = false, error = null) }
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                require(!webClientId.startsWith("REPLACE")) {
                    "Set default_web_client_id in strings.xml from Firebase console"
                }

                val idToken = requestGoogleIdToken(context, webClientId)
                val profile = authRepository.signInWithGoogle(idToken)
                _uiState.update { it.copy(isLoading = false, profile = profile) }
            } catch (e: GetCredentialException) {
                _uiState.update { it.copy(isLoading = false, error = e.toGoogleSignInMessage()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toAuthMessage()) }
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRestoringSession = false, error = null) }
            try {
                val profile = authRepository.signInAnonymously()
                _uiState.update { it.copy(isLoading = false, profile = profile) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toAuthMessage()) }
            }
        }
    }

    fun submitEmailAuth(context: Context) {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password

        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRestoringSession = false, error = null) }
            try {
                val profile = when (state.emailMode) {
                    EmailAuthMode.SIGN_IN -> authRepository.signInWithEmail(email, password)
                    EmailAuthMode.REGISTER -> authRepository.registerWithEmail(
                        email = email,
                        password = password,
                        displayName = state.displayName,
                    )
                }
                offerSavePassword(
                    context = context,
                    email = email,
                    password = password,
                )
                _uiState.update { it.copy(isLoading = false, profile = profile) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toAuthMessage()) }
            }
        }
    }

    /**
     * Button sign-in uses [GetSignInWithGoogleOption] (account picker). Falls back to
     * [GetGoogleIdOption] if the picker flow is unavailable on the device.
     */
    private suspend fun requestGoogleIdToken(context: Context, webClientId: String): String {
        val credentialManager = CredentialManager.create(context)
        val signInWithGoogle = GetSignInWithGoogleOption.Builder(webClientId).build()
        val signInRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogle)
            .build()

        return try {
            extractGoogleIdToken(credentialManager.getCredential(context, signInRequest).credential)
        } catch (_: GetCredentialException) {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val googleIdRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            extractGoogleIdToken(credentialManager.getCredential(context, googleIdRequest).credential)
        }
    }

    private fun extractGoogleIdToken(credential: androidx.credentials.Credential): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type")
    }
}
