package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.PresenceRepository
import com.rpsonline.app.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val onlinePlayerCount: Int? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var presenceJob: Job? = null
    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            presenceRepository.observeOnlineCount().collect { count ->
                _uiState.update { it.copy(onlinePlayerCount = count) }
            }
        }
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                profileJob?.cancel()
                profileJob = null
                if (user == null) {
                    stopPresenceHeartbeat()
                    _uiState.update {
                        it.copy(isLoading = false, profile = null, onlinePlayerCount = null)
                    }
                } else {
                    profileJob = viewModelScope.launch {
                        userRepository.observeUserProfile(user.uid).collect { profile ->
                            if (profile != null) {
                                _uiState.update { it.copy(profile = profile, isLoading = false) }
                            }
                        }
                    }
                    refresh(user)
                }
            }
        }
    }

    fun onHomeVisible() {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.currentUser ?: return@launch
            refresh(user)
        }
    }

    private suspend fun refresh(user: FirebaseUser) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            authRepository.ensureUserProfile(
                uid = user.uid,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
            )
            _uiState.update { it.copy(isLoading = false) }
            startPresenceHeartbeat(user.uid)
        } catch (e: Exception) {
            stopPresenceHeartbeat()
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private fun startPresenceHeartbeat(uid: String) {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            presenceRepository.touchPresence(uid)
            while (isActive) {
                delay(PresenceRepository.HEARTBEAT_INTERVAL_MS)
                presenceRepository.touchPresence(uid)
            }
        }
    }

    private fun stopPresenceHeartbeat() {
        presenceJob?.cancel()
        presenceJob = null
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            stopPresenceHeartbeat()
            authRepository.currentUserId?.let { uid ->
                presenceRepository.clearPresence(uid)
            }
            authRepository.signOut(context)
            _uiState.update {
                HomeUiState(
                    isLoading = false,
                    profile = null,
                    onlinePlayerCount = null,
                )
            }
        }
    }

    override fun onCleared() {
        profileJob?.cancel()
        stopPresenceHeartbeat()
        super.onCleared()
    }
}
