package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                if (user == null) {
                    _uiState.update { it.copy(isLoading = false, profile = null) }
                } else {
                    refresh(user)
                }
            }
        }
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
            val profile = authRepository.ensureUserProfile(
                uid = user.uid,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
            )
            val leaderboard = userRepository.getLeaderboard(limit = 10)
            _uiState.update {
                it.copy(isLoading = false, profile = profile, leaderboard = leaderboard)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
            _uiState.update {
                HomeUiState(isLoading = false, profile = null, leaderboard = emptyList())
            }
        }
    }
}
