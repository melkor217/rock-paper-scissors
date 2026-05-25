package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.UserRepository
import com.rpsonline.app.domain.enrichMatchHistoryWithOpponentElos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val matchHistory: List<MatchHistoryEntry> = emptyList(),
    val isOwnProfile: Boolean = true,
    val error: String? = null,
)

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val matchRepository: MatchRepository = MatchRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedUserId: String? = null

    fun load(userId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val switchingPlayer = loadedUserId != null && loadedUserId != userId
            val showFullScreenLoading = _uiState.value.profile == null || switchingPlayer
            if (switchingPlayer) {
                _uiState.update {
                    ProfileUiState(
                        isLoading = true,
                        isOwnProfile = userId == authRepository.currentUserId,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = showFullScreenLoading,
                        error = null,
                    )
                }
            }
            try {
                val viewerId = authRepository.currentUserId
                    ?: throw IllegalStateException("Not signed in")
                val profile = userRepository.getUserProfile(userId)
                    ?: throw IllegalStateException("Player not found")
                val isOwnProfile = userId == viewerId
                val matches = if (isOwnProfile) {
                    matchRepository.getRecentMatchesForUser(viewerId, limit = 10)
                } else {
                    matchRepository.getRecentSharedMatches(
                        viewerId = viewerId,
                        opponentId = userId,
                        limit = 10,
                    )
                }
                val history = enrichMatchHistoryWithOpponentElos(
                    viewerId = viewerId,
                    myCurrentElo = profile.elo,
                    matches = matches,
                )
                loadedUserId = userId
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        matchHistory = history,
                        isOwnProfile = isOwnProfile,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load profile",
                    )
                }
            }
        }
    }
}
