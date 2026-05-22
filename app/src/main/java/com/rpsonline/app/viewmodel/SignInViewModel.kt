package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rpsonline.app.R
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null,
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
                    try {
                        val profile = authRepository.loadCurrentUserProfile()
                            ?: authRepository.ensureUserProfile(
                                uid = user.uid,
                                displayName = user.displayName,
                                photoUrl = user.photoUrl?.toString(),
                            )
                        _uiState.update { it.copy(profile = profile, isLoading = false) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(profile = null, isLoading = false) }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                require(!webClientId.startsWith("REPLACE")) {
                    "Set default_web_client_id in strings.xml from Firebase console"
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val profile = authRepository.signInWithGoogle(googleCredential.idToken)
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Unexpected credential type") }
                }
            } catch (e: GetCredentialException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign-in cancelled") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }
}
