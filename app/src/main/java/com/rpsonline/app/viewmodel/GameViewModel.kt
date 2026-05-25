package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PRE_GAME_COUNTDOWN_SECONDS = 3

data class GameUiState(
    val match: Match? = null,
    val userId: String? = null,
    val myProfile: UserProfile? = null,
    val opponentProfile: UserProfile? = null,
    val showPreGameCountdown: Boolean = false,
    val preGameCountdownSeconds: Int = PRE_GAME_COUNTDOWN_SECONDS,
    val hasSubmittedMove: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val countdownSeconds: Int? = null,
    val isResolvingTimeout: Boolean = false,
    val lockedMove: Move? = null,
)

class GameViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository = MatchRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState(userId = authRepository.currentUserId))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var countdownJob: Job? = null
    private var preGameCountdownJob: Job? = null
    private var preGameCountdownStarted = false
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
                maybeStartPreGameCountdown(match, userId)
            }
        }
    }

    private fun maybeStartPreGameCountdown(match: Match?, userId: String?) {
        if (preGameCountdownStarted || match == null || userId == null) return
        if (match.status != MatchStatus.ACTIVE) return
        preGameCountdownStarted = true
        viewModelScope.launch {
            val opponentId = match.opponentId(userId)
            val myProfile = userRepository.getUserProfile(userId)
            val opponentProfile = userRepository.getUserProfile(opponentId)
            _uiState.update {
                it.copy(
                    myProfile = myProfile,
                    opponentProfile = opponentProfile,
                )
            }
            startPreGameCountdown()
        }
    }

    private fun startPreGameCountdown() {
        preGameCountdownJob?.cancel()
        preGameCountdownJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showPreGameCountdown = true,
                    preGameCountdownSeconds = PRE_GAME_COUNTDOWN_SECONDS,
                )
            }
            for (seconds in PRE_GAME_COUNTDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(preGameCountdownSeconds = seconds) }
                delay(1_000)
                if (!_uiState.value.showPreGameCountdown) return@launch
            }
            _uiState.update {
                it.copy(
                    showPreGameCountdown = false,
                    preGameCountdownSeconds = 0,
                )
            }
        }
    }

    fun skipPreGameCountdown() {
        preGameCountdownJob?.cancel()
        _uiState.update {
            it.copy(
                showPreGameCountdown = false,
                preGameCountdownSeconds = 0,
            )
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
                    if (timeoutRequestedForRound != roundNumber ||
                        (!_uiState.value.isResolvingTimeout && _uiState.value.error != null)
                    ) {
                        requestTimeoutResolution(match, roundNumber)
                    }
                    delay(500)
                    continue
                }
                delay(250)
            }
        }
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
        preGameCountdownJob?.cancel()
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
