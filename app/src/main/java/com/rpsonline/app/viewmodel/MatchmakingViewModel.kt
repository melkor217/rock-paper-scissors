package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MatchmakingStatus {
    IDLE,
    SEARCHING,
    MATCHED,
    ERROR,
}

data class MatchmakingUiState(
    val status: MatchmakingStatus = MatchmakingStatus.IDLE,
    val matchId: String? = null,
    val match: Match? = null,
    val error: String? = null,
)

class MatchmakingViewModel(
    private val matchRepository: MatchRepository = MatchRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchmakingUiState())
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun startMatchmaking() {
        if (_uiState.value.status == MatchmakingStatus.SEARCHING) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(status = MatchmakingStatus.SEARCHING, error = null, matchId = null, match = null)
            }
            try {
                if (authRepository.currentUserId == null) {
                    _uiState.update {
                        it.copy(
                            status = MatchmakingStatus.ERROR,
                            error = "Not signed in. Go back and sign in with Google again.",
                        )
                    }
                    return@launch
                }
                val immediateMatchId = matchRepository.joinQueue()
                if (immediateMatchId != null) {
                    _uiState.update {
                        it.copy(status = MatchmakingStatus.MATCHED, matchId = immediateMatchId)
                    }
                    return@launch
                }
                observeForMatch()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(status = MatchmakingStatus.ERROR, error = e.message ?: "Matchmaking failed")
                }
            }
        }
    }

    private fun observeForMatch() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            matchRepository.observeActiveMatch().collect { match ->
                if (match != null) {
                    _uiState.update {
                        it.copy(
                            status = MatchmakingStatus.MATCHED,
                            matchId = match.id,
                            match = match,
                        )
                    }
                    observeJob?.cancel()
                }
            }
        }
    }

    fun cancelMatchmaking() {
        viewModelScope.launch {
            try {
                matchRepository.leaveQueue()
            } catch (_: Exception) {
            } finally {
                observeJob?.cancel()
                _uiState.update {
                    MatchmakingUiState(status = MatchmakingStatus.IDLE)
                }
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
