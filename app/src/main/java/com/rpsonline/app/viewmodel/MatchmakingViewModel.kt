package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.PresenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val matchModes: Set<MatchMode> = MatchMode.DEFAULT_SELECTION,
    val matchId: String? = null,
    val match: Match? = null,
    val error: String? = null,
    val queueElapsedSeconds: Long = 0,
    val onlinePlayerCount: Int? = null,
)

class MatchmakingViewModel(
    private val matchModes: Set<MatchMode> = MatchMode.DEFAULT_SELECTION,
    private val matchRepository: MatchRepository = MatchRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchmakingUiState(matchModes = matchModes))
    val uiState: StateFlow<MatchmakingUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var queueTimerJob: Job? = null

    init {
        viewModelScope.launch {
            presenceRepository.observeOnlineCount().collect { count ->
                _uiState.update { it.copy(onlinePlayerCount = count) }
            }
        }
    }

    fun startMatchmaking() {
        if (_uiState.value.status == MatchmakingStatus.SEARCHING) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = MatchmakingStatus.SEARCHING,
                    error = null,
                    matchId = null,
                    match = null,
                    queueElapsedSeconds = 0,
                )
            }
            startQueueTimer()
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
                val immediateMatchId = matchRepository.joinQueue(matchModes)
                if (immediateMatchId != null) {
                    stopQueueTimer()
                    _uiState.update {
                        it.copy(status = MatchmakingStatus.MATCHED, matchId = immediateMatchId)
                    }
                    return@launch
                }
                observeForMatch()
            } catch (e: Exception) {
                stopQueueTimer()
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
                    stopQueueTimer()
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
                stopQueueTimer()
                _uiState.update {
                    it.copy(
                        status = MatchmakingStatus.IDLE,
                        error = null,
                        matchId = null,
                        match = null,
                        queueElapsedSeconds = 0,
                    )
                }
            }
        }
    }

    private fun startQueueTimer() {
        queueTimerJob?.cancel()
        val startedAtMs = System.currentTimeMillis()
        queueTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (_uiState.value.status != MatchmakingStatus.SEARCHING) break
                val elapsed = (System.currentTimeMillis() - startedAtMs) / 1_000
                _uiState.update { it.copy(queueElapsedSeconds = elapsed) }
            }
        }
    }

    private fun stopQueueTimer() {
        queueTimerJob?.cancel()
        queueTimerJob = null
    }

    override fun onCleared() {
        observeJob?.cancel()
        stopQueueTimer()
        super.onCleared()
    }

    companion object {
        fun factory(matchModes: Set<MatchMode>): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MatchmakingViewModel(matchModes = matchModes) as T
                }
            }
    }
}
