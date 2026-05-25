package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val entries: List<LeaderboardEntry> = emptyList(),
    val currentUserId: String? = null,
    val error: String? = null,
)

class LeaderboardViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LeaderboardUiState(currentUserId = authRepository.currentUserId),
    )
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val showFullScreenLoading = _uiState.value.entries.isEmpty()
            _uiState.update {
                it.copy(
                    isLoading = showFullScreenLoading,
                    error = null,
                )
            }
            try {
                val userId = authRepository.currentUserId
                val entries = userRepository.getLeaderboard(limit = 50)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        currentUserId = userId,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
            }
        }
    }
}
