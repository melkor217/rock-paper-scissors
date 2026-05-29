package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundResult
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.FirestoreConnectivity
import com.rpsonline.app.data.repository.GameFunctions
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.domain.GameRules
import com.rpsonline.app.ui.game.computeRoundSecondsFromAnchor
import com.rpsonline.app.ui.game.roundElapsedAtSyncMs
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
    val pendingMove: Move? = null,
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
    private var roundCountdownRoundKey: String? = null
    private var roundCountdownElapsedAtSyncMs: Long = 0L
    private var roundCountdownSyncRealtimeMs: Long = 0L
    private var clockSyncRealtimeMs: Long = 0L
    private var clockSyncMyBaseMs: Long = 0L
    private var clockSyncOppBaseMs: Long = 0L
    private var clockSyncMyRunning: Boolean = false
    private var clockSyncOppRunning: Boolean = false
    private var lastClockFingerprint: String? = null
    private var timeoutRequestedForRound: Int? = null
    private var lockedMoveRound: Int? = null
    private var locallySubmittedRound: Int? = null
    private var lastObservedOpenRound: Int? = null
    private var submitGeneration = 0
    private var submitWatchdogJob: Job? = null
    private var matchSnapshotAtTimeoutRequest: String? = null
    private var resolvingRetryJob: Job? = null
    init {
        observeJob = viewModelScope.launch {
            matchRepository.observeMatch(matchId).collect { match ->
                applyMatchSnapshot(match)
            }
        }
    }

    fun refreshOnResume() {
        viewModelScope.launch {
            runCatching {
                FirestoreConnectivity.restoreOnResume()
                MatchSessionMonitor.refreshOnResume()
                syncMatchFromServer()
            }
        }
    }

    private suspend fun syncMatchFromServer() {
        val match = matchRepository.getMatchFromServer(matchId) ?: return
        applyMatchSnapshot(match)
    }

    private fun applyMatchSnapshot(match: Match?) {
        val userId = authRepository.currentUserId
        val openRound = match?.openRound()
        val openRoundNumber = openRound?.roundNumber
        if (openRoundNumber != lastObservedOpenRound) {
            if (lastObservedOpenRound != null) {
                submitGeneration++
                locallySubmittedRound = null
            }
            lastObservedOpenRound = openRoundNumber
        }

        val alreadySubmitted = when {
            match == null || userId == null || openRound == null -> false
            userId == match.player1 -> openRound.player1Choice != null
            else -> openRound.player2Choice != null
        }
        if (alreadySubmitted && openRoundNumber == locallySubmittedRound) {
            locallySubmittedRound = null
        }
        val hasSubmittedMove = alreadySubmitted ||
            (openRoundNumber != null && locallySubmittedRound == openRoundNumber)

        val serverChoice = when {
            match == null || userId == null || openRound == null -> null
            userId == match.player1 -> openRound.player1Choice
            else -> openRound.player2Choice
        }
        val lockedMove = when {
            !hasSubmittedMove -> {
                lockedMoveRound = null
                null
            }
            alreadySubmitted && openRoundNumber != lockedMoveRound -> {
                lockedMoveRound = openRoundNumber
                Move.fromString(serverChoice) ?: _uiState.value.lockedMove
            }
            alreadySubmitted -> Move.fromString(serverChoice) ?: _uiState.value.lockedMove
            else -> _uiState.value.pendingMove ?: _uiState.value.lockedMove
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
                hasSubmittedMove = hasSubmittedMove,
                lockedMove = lockedMove,
                // Server confirmed our choice — do not keep "Communicating to server" until write() returns.
                isSubmitting = if (alreadySubmitted) false else it.isSubmitting,
                pendingMove = if (hasSubmittedMove) null else it.pendingMove,
                isResolvingTimeout = if (clearResolving) false else it.isResolvingTimeout,
            )
        }
        if (openRound?.roundNumber != timeoutRequestedForRound) {
            timeoutRequestedForRound = null
        }
        syncCountdown(match)
        syncMatchClocks(match, userId)
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

    private fun resyncClockAnchor(
        match: Match,
        userId: String,
        myRunning: Boolean,
        oppRunning: Boolean,
    ) {
        clockSyncRealtimeMs = SystemClock.elapsedRealtime()
        clockSyncMyBaseMs = match.myClockMs(userId)
        clockSyncOppBaseMs = match.opponentClockMs(userId)
        clockSyncMyRunning = myRunning
        clockSyncOppRunning = oppRunning
    }

    private fun syncMatchClocks(match: Match?, userId: String?) {
        if (match?.status != MatchStatus.ACTIVE || userId == null || match.openRound() == null) {
            clockJob?.cancel()
            lastClockFingerprint = null
            _uiState.update { it.copy(myClockSeconds = null, opponentClockSeconds = null) }
            return
        }

        val openRound = match.openRound()!!
        val hasSubmitted = _uiState.value.hasSubmittedMove
        val opponentSubmitted = when (userId) {
            match.player1 -> openRound.player2Choice != null
            else -> openRound.player1Choice != null
        }
        val fingerprint = buildString {
            append(match.clocksUpdatedAt)
            append('|')
            append(match.player1ClockMs)
            append('|')
            append(match.player2ClockMs)
            append('|')
            append(hasSubmitted)
            append('|')
            append(opponentSubmitted)
        }
        if (fingerprint != lastClockFingerprint) {
            lastClockFingerprint = fingerprint
            resyncClockAnchor(match, userId, myRunning = !hasSubmitted, oppRunning = !opponentSubmitted)
        }

        if (clockJob?.isActive == true) return

        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            val maxClockSeconds = (GameRules.MAX_CLOCK_MS / 1_000).toInt()
            while (true) {
                val state = _uiState.value
                val activeMatch = state.match ?: break
                if (activeMatch.status != MatchStatus.ACTIVE) break

                val elapsed = SystemClock.elapsedRealtime() - clockSyncRealtimeMs
                val myMs = if (clockSyncMyRunning) {
                    (clockSyncMyBaseMs - elapsed).coerceAtLeast(0L)
                } else {
                    clockSyncMyBaseMs
                }
                val oppMs = if (clockSyncOppRunning) {
                    (clockSyncOppBaseMs - elapsed).coerceAtLeast(0L)
                } else {
                    clockSyncOppBaseMs
                }

                val mySeconds = ((myMs + 999) / 1000).toInt().coerceIn(0, maxClockSeconds)
                val oppSeconds = ((oppMs + 999) / 1000).toInt().coerceIn(0, maxClockSeconds)
                _uiState.update {
                    it.copy(myClockSeconds = mySeconds, opponentClockSeconds = oppSeconds)
                }

                if (clockSyncMyRunning && myMs <= 0L) {
                    val open = activeMatch.openRound() ?: break
                    maybeRequestTimeoutResolution(activeMatch, open.roundNumber)
                }

                delay(100)
            }
        }
    }

    private fun resyncRoundCountdownAnchor(openRound: RoundResult) {
        val startMs = openRound.roundStartMs() ?: return
        roundCountdownElapsedAtSyncMs = roundElapsedAtSyncMs(startMs, System.currentTimeMillis())
        roundCountdownSyncRealtimeMs = SystemClock.elapsedRealtime()
    }

    private fun currentRoundSecondsRemaining(): Int =
        computeRoundSecondsFromAnchor(
            elapsedAtSyncMs = roundCountdownElapsedAtSyncMs,
            syncElapsedRealtimeMs = roundCountdownSyncRealtimeMs,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )

    private fun syncCountdown(match: Match?) {
        val openRound = match?.openRound()
        if (match?.status != MatchStatus.ACTIVE || openRound == null || openRound.roundStartMs() == null) {
            countdownJob?.cancel()
            roundCountdownRoundKey = null
            _uiState.update { it.copy(countdownSeconds = null, isResolvingTimeout = false) }
            return
        }
        val roundKey = "${openRound.roundNumber}:${openRound.roundStartMs()}"
        val needsRestart = roundKey != roundCountdownRoundKey || countdownJob?.isActive != true
        if (needsRestart) {
            roundCountdownRoundKey = roundKey
            resyncRoundCountdownAnchor(openRound)
            timeoutRequestedForRound = null
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                val roundNumber = openRound.roundNumber
                while (true) {
                    val currentOpen = _uiState.value.match?.openRound()
                    if (currentOpen?.roundNumber != roundNumber) break

                    val seconds = currentRoundSecondsRemaining()
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
        } else {
            _uiState.update { it.copy(countdownSeconds = currentRoundSecondsRemaining()) }
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isIgnorableFirestoreRace(e)) {
                        matchSnapshotAtTimeoutRequest = null
                        _uiState.update { it.copy(isResolvingTimeout = false, error = null) }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            isResolvingTimeout = false,
                            error = userFacingGameError(e, "Failed to resolve timeout"),
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
        if (
            state.hasSubmittedMove ||
            state.isSubmitting ||
            state.pendingMove != null ||
            state.match?.status != MatchStatus.ACTIVE
        ) {
            return
        }

        val roundNumber = state.match?.currentRound ?: return
        val generation = ++submitGeneration

        viewModelScope.launch {
            submitWatchdogJob?.cancel()
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    pendingMove = move,
                    lockedMove = move,
                    error = null,
                )
            }
            val serverSyncJob = launch {
                while (generation == submitGeneration) {
                    delay(400)
                    if (!_uiState.value.isSubmitting) break
                    runCatching { syncMatchFromServer() }
                }
            }
            submitWatchdogJob = launch {
                delay(SUBMIT_STUCK_WATCHDOG_MS)
                if (generation != submitGeneration) return@launch
                if (!_uiState.value.isSubmitting) return@launch
                revertMoveIfServerMissing(
                    generation,
                    roundNumber,
                ) { "Move is taking too long to reach the server. Tap a move to try again." }
            }
            try {
                withTimeout(SUBMIT_MOVE_TIMEOUT_MS) {
                    matchRepository.submitMove(matchId, move, roundNumber)
                }
                submitWatchdogJob?.cancel()
                serverSyncJob.cancel()
                if (generation != submitGeneration) return@launch
                syncMatchFromServer()
                locallySubmittedRound = roundNumber
                lockedMoveRound = roundNumber
                _uiState.update {
                    it.copy(
                        hasSubmittedMove = true,
                        lockedMove = move,
                        isSubmitting = false,
                        pendingMove = null,
                    )
                }
            } catch (e: CancellationException) {
                serverSyncJob.cancel()
                submitWatchdogJob?.cancel()
                throw e
            } catch (e: TimeoutCancellationException) {
                submitWatchdogJob?.cancel()
                serverSyncJob.cancel()
                if (generation != submitGeneration) return@launch
                revertMoveIfServerMissing(
                    generation,
                    roundNumber,
                ) { "Connection timed out. Check your network and tap a move to try again." }
            } catch (e: Exception) {
                submitWatchdogJob?.cancel()
                serverSyncJob.cancel()
                if (generation != submitGeneration) return@launch
                if (isIgnorableFirestoreRace(e)) {
                    _uiState.update { it.copy(isSubmitting = false, pendingMove = null, error = null) }
                    return@launch
                }
                revertMoveIfServerMissing(
                    generation,
                    roundNumber,
                ) {
                    GameFunctions.toSubmitErrorMessage(e)
                        ?: userFacingGameError(e, "Failed to submit move")
                }
            }
        }
    }

    private suspend fun revertMoveIfServerMissing(
        generation: Int,
        roundNumber: Int,
        errorMessage: () -> String,
    ) {
        if (generation != submitGeneration) return
        runCatching { syncMatchFromServer() }
        val userId = authRepository.currentUserId
        val match = _uiState.value.match ?: matchRepository.getMatchFromServer(matchId)
        val open = match?.openRound()
        val serverHasChoice = userId != null && match != null &&
            open?.roundNumber == roundNumber &&
            when (userId) {
                match.player1 -> open.player1Choice != null
                else -> open.player2Choice != null
            }
        if (serverHasChoice) {
            locallySubmittedRound = roundNumber
            lockedMoveRound = roundNumber
            _uiState.update {
                it.copy(
                    match = match,
                    hasSubmittedMove = true,
                    lockedMove = it.lockedMove ?: it.pendingMove,
                    isSubmitting = false,
                    pendingMove = null,
                    error = null,
                )
            }
            return
        }
        locallySubmittedRound = null
        lockedMoveRound = null
        _uiState.update {
            it.copy(
                hasSubmittedMove = false,
                lockedMove = null,
                isSubmitting = false,
                pendingMove = null,
                error = errorMessage(),
            )
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
        private const val SUBMIT_MOVE_TIMEOUT_MS = 12_000L
        private const val SUBMIT_STUCK_WATCHDOG_MS = 14_000L

        fun factory(matchId: String): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(matchId) as T
                }
            }
    }
}

/** Round already advanced or listener cancelled — not worth showing to the player. */
private fun isIgnorableFirestoreRace(error: Throwable): Boolean {
    if (error is CancellationException) return true
    if (error is FirebaseFirestoreException &&
        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    ) {
        return true
    }
    val message = error.message.orEmpty()
    if (message.contains("PERMISSION_DENIED", ignoreCase = true)) return true
    if (message.contains("StandaloneCoroutine was cancelled", ignoreCase = true)) return true
    val cause = error.cause ?: return false
    if (cause === error) return false
    return isIgnorableFirestoreRace(cause)
}

private fun userFacingGameError(error: Throwable, fallback: String): String {
    if (error is FirebaseFirestoreException) {
        return when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> "Connection issue. Check your network and try again."
            else -> fallback
        }
    }
    return error.message?.takeIf { it.isNotBlank() } ?: fallback
}
