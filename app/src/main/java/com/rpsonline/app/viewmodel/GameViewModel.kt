package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val match: Match? = null,
    val userId: String? = null,
    val hasSubmittedMove: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val countdownSeconds: Int? = null,
)

class GameViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository = MatchRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState(userId = authRepository.currentUserId))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeJob = viewModelScope.launch {
            matchRepository.observeMatch(matchId).collect { match ->
                val userId = authRepository.currentUserId
                val currentRound = match?.currentRoundData()
                val alreadySubmitted = when {
                    match == null || userId == null || currentRound == null -> false
                    userId == match.player1 -> currentRound.player1Choice != null
                    else -> currentRound.player2Choice != null
                }
                _uiState.update {
                    it.copy(
                        match = match,
                        userId = userId,
                        hasSubmittedMove = alreadySubmitted,
                        isSubmitting = false,
                    )
                }
            }
        }
    }

    fun submitMove(move: Move) {
        val state = _uiState.value
        if (state.hasSubmittedMove || state.isSubmitting || state.match?.status != MatchStatus.ACTIVE) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val roundNumber = state.match?.currentRound ?: return@launch
                matchRepository.submitMove(matchId, move, roundNumber)
                _uiState.update { it.copy(hasSubmittedMove = true, isSubmitting = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = e.message ?: "Failed to submit move")
                }
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(matchId: String): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(matchId) as T
                }
            }
    }
}
