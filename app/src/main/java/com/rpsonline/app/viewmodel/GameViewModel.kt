package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val myClockSeconds: Int? = null,
    val opponentClockSeconds: Int? = null,
    val isResolvingTimeout: Boolean = false,
    val lockedMove: Move? = null,
)

class GameViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository = MatchRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState(userId = authRepository.currentUserId))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var countdownJob: Job? = null
    private var clockJob: Job? = null
    private var countdownDeadlineMs: Long? = null
    private var timeoutRequestedForRound: Int? = null
    private var lockedMoveRound: Int? = null
    private var matchSnapshotAtTimeoutRequest: String? = null
    private var resolvingRetryJob: Job? = null

    init {
        observeJob = viewModelScope.launch {
            matchRepository.observeMatch(matchId).collect { match ->
                val userId = authRepository.currentUserId
                val openRound = match?.openRound()
                val alreadySubmitted = when {
                    match == null || userId == null || openRound == null -> false
                    userId == match.player1 -> openRound.player1Choice != null
                    else -> openRound.player2Choice != null
                }
                val serverChoice = when {
                    match == null || userId == null || openRound == null -> null
                    userId == match.player1 -> openRound.player1Choice
                    else -> openRound.player2Choice
                }
                val openRoundNumber = openRound?.roundNumber
                val lockedMove = when {
                    !alreadySubmitted -> {
                        lockedMoveRound = null
                        null
                    }
                    openRoundNumber != lockedMoveRound -> {
                        lockedMoveRound = openRoundNumber
                        Move.fromString(serverChoice) ?: _uiState.value.lockedMove
                    }
                    else -> Move.fromString(serverChoice) ?: _uiState.value.lockedMove
                }

                val fingerprint = match?.let { matchFingerprint(it) }
                val serverResponded = matchSnapshotAtTimeoutRequest != null &&
                    fingerprint != null &&
                    fingerprint != matchSnapshotAtTimeoutRequest
                val clearResolving = _uiState.value.isResolvingTimeout && serverResponded

                if (clearResolving) {
                    matchSnapshotAtTimeoutRequest = null
                    resolvingRetryJob?.cancel()
                    resolvingRetryJob = null
                }

                _uiState.update {
                    it.copy(
                        match = match,
                        userId = userId,
                        hasSubmittedMove = alreadySubmitted,
                        lockedMove = lockedMove,
                        isSubmitting = false,
                        isResolvingTimeout = if (clearResolving) false else it.isResolvingTimeout,
                    )
                }
                if (openRound?.roundNumber != timeoutRequestedForRound) {
                    timeoutRequestedForRound = null
                }
                syncCountdown(match)
                syncMatchClocks(match, userId)
            }
        }
    }

    private fun matchFingerprint(match: Match): String {
        val open = match.openRound()
        return buildString {
            append(match.status)
            append('|')
            append(match.lastActivityAt)
            append('|')
            append(match.rounds.size)
            append('|')
            append(match.player1Wins)
            append('|')
            append(match.player2Wins)
            append('|')
            append(open?.roundNumber)
            append('|')
            append(open?.deadline)
            append('|')
            append(open?.player1Choice)
            append('|')
            append(open?.player2Choice)
            append('|')
            append(open?.resolvedAt)
            append('|')
            append(match.player1ClockMs)
            append('|')
            append(match.player2ClockMs)
            append('|')
            append(match.clocksUpdatedAt)
        }
    }

    private fun syncMatchClocks(match: Match?, userId: String?) {
        if (match?.status != MatchStatus.ACTIVE || userId == null || match.openRound() == null) {
            clockJob?.cancel()
            _uiState.update { it.copy(myClockSeconds = null, opponentClockSeconds = null) }
            return
        }

        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val activeMatch = state.match ?: break
                if (activeMatch.status != MatchStatus.ACTIVE) break

                val openRound = activeMatch.openRound() ?: break
                val opponentSubmitted = when (userId) {
                    activeMatch.player1 -> openRound.player2Choice != null
                    else -> openRound.player1Choice != null
                }

                val updatedAt = activeMatch.clocksUpdatedAt.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                val elapsed = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)

                val myBase = activeMatch.myClockMs(userId)
                val oppBase = activeMatch.opponentClockMs(userId)
                val myMs = if (state.hasSubmittedMove) {
                    myBase
                } else {
                    (myBase - elapsed).coerceAtLeast(0L)
                }
                val oppMs = if (opponentSubmitted) {
                    oppBase
                } else {
                    (oppBase - elapsed).coerceAtLeast(0L)
                }

                val mySeconds = ((myMs + 999) / 1000).toInt()
                val oppSeconds = ((oppMs + 999) / 1000).toInt()
                _uiState.update {
                    it.copy(myClockSeconds = mySeconds, opponentClockSeconds = oppSeconds)
                }

                if (!state.hasSubmittedMove && myMs <= 0L) {
                    maybeRequestTimeoutResolution(activeMatch, openRound.roundNumber)
                }

                delay(100)
            }
        }
    }

    private fun syncCountdown(match: Match?) {
        val openRound = match?.openRound()
        if (match?.status != MatchStatus.ACTIVE || openRound == null || openRound.deadline == null) {
            countdownJob?.cancel()
            countdownDeadlineMs = null
            _uiState.update { it.copy(countdownSeconds = null, isResolvingTimeout = false) }
            return
        }
        val deadline = openRound.deadline
        if (deadline == countdownDeadlineMs) return
        countdownDeadlineMs = deadline
        timeoutRequestedForRound = null
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            val roundNumber = openRound.roundNumber
            while (true) {
                val remainingMs = deadline - System.currentTimeMillis()
                val seconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
                _uiState.update { it.copy(countdownSeconds = seconds) }
                if (seconds <= 0) {
                    val state = _uiState.value
                    val matchClockWillResolve = !state.hasSubmittedMove && state.myClockSeconds == 0
                    if (!matchClockWillResolve) {
                        maybeRequestTimeoutResolution(match, roundNumber)
                    }
                    delay(500)
                    continue
                }
                delay(250)
            }
        }
    }

    /**
     * Request server resolution once per round. Match clock takes precedence over round
     * deadline when both expire (server checks clock expiry first).
     */
    private fun maybeRequestTimeoutResolution(match: Match, roundNumber: Int) {
        if (timeoutRequestedForRound == roundNumber) {
            val state = _uiState.value
            if (state.isResolvingTimeout) return
            if (state.error == null) return
        }
        requestTimeoutResolution(match, roundNumber)
    }

    private fun requestTimeoutResolution(match: Match, roundNumber: Int) {
        timeoutRequestedForRound = roundNumber
        matchSnapshotAtTimeoutRequest = matchFingerprint(match)
        _uiState.update { it.copy(isResolvingTimeout = true, error = null) }
        resolvingRetryJob?.cancel()
        resolvingRetryJob = viewModelScope.launch {
            repeat(6) { attempt ->
                if (!_uiState.value.isResolvingTimeout || matchSnapshotAtTimeoutRequest == null) {
                    return@launch
                }
                try {
                    matchRepository.requestRoundTimeout(matchId, roundNumber)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isResolvingTimeout = false,
                            error = e.message ?: "Failed to resolve timeout",
                        )
                    }
                    matchSnapshotAtTimeoutRequest = null
                    return@launch
                }
                if (attempt < 5) {
                    delay(3_000)
                }
            }
            if (_uiState.value.isResolvingTimeout) {
                _uiState.update {
                    it.copy(
                        isResolvingTimeout = false,
                        error = "Round resolution is taking longer than expected. It should update shortly.",
                    )
                }
                matchSnapshotAtTimeoutRequest = null
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
                lockedMoveRound = roundNumber
                _uiState.update {
                    it.copy(hasSubmittedMove = true, lockedMove = move, isSubmitting = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = e.message ?: "Failed to submit move")
                }
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        countdownJob?.cancel()
        clockJob?.cancel()
        resolvingRetryJob?.cancel()
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
