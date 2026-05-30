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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    /** True once the server match doc records our submission for this round. */
    val serverMoveSubmitted: Boolean = false,
    val opponentHasSubmitted: Boolean = false,
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
    private var activeSubmitJob: Job? = null
    private var matchSnapshotAtTimeoutRequest: String? = null
    private var resolvingRetryJob: Job? = null
    private var stuckRoundNudgeJob: Job? = null
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
        if (match == null) {
            val current = _uiState.value.match
            if (current?.status == MatchStatus.COMPLETED || current?.status == MatchStatus.ABANDONED) {
                return
            }
        }
        val userId = authRepository.currentUserId
        val openRound = match?.openRound()
        val openRoundNumber = openRound?.roundNumber
        if (openRoundNumber != lastObservedOpenRound) {
            if (lastObservedOpenRound != null) {
                cancelInFlightSubmit(clearPendingUi = true)
                locallySubmittedRound = null
                lockedMoveRound = null
            }
            lastObservedOpenRound = openRoundNumber
        }

        val alreadySubmitted = when {
            match == null || userId == null || openRound == null -> false
            else -> openRound.hasSubmittedFor(userId, match.player1)
        }
        if (alreadySubmitted && openRoundNumber == locallySubmittedRound) {
            locallySubmittedRound = null
        }
        if (!alreadySubmitted && openRoundNumber != null && locallySubmittedRound == openRoundNumber) {
            locallySubmittedRound = null
        }
        val localSubmitPending = locallySubmittedRound != null &&
            match?.status == MatchStatus.ACTIVE &&
            (openRoundNumber == null || locallySubmittedRound == openRoundNumber)
        val hasSubmittedMove = alreadySubmitted || localSubmitPending
        val opponentHasSubmitted = when {
            match == null || userId == null || openRound == null -> false
            else -> openRound.opponentHasSubmittedFor(userId, match.player1)
        }

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
            alreadySubmitted && openRoundNumber != null && openRoundNumber != lockedMoveRound -> {
                lockedMoveRound = openRoundNumber
                Move.fromString(serverChoice) ?: _uiState.value.lockedMove ?: _uiState.value.pendingMove
            }
            alreadySubmitted -> Move.fromString(serverChoice) ?: _uiState.value.lockedMove ?: _uiState.value.pendingMove
            locallySubmittedRound == openRoundNumber ->
                _uiState.value.pendingMove ?: _uiState.value.lockedMove
            else -> null
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
                serverMoveSubmitted = alreadySubmitted,
                opponentHasSubmitted = opponentHasSubmitted,
                lockedMove = lockedMove,
                // Server confirmed our choice — do not keep "Communicating to server" until write() returns.
                isSubmitting = if (alreadySubmitted) false else it.isSubmitting,
                pendingMove = when {
                    !hasSubmittedMove -> null
                    lockedMove != null -> null
                    else -> it.pendingMove
                },
                isResolvingTimeout = if (clearResolving) false else it.isResolvingTimeout,
            )
        }
        if (openRound?.roundNumber != timeoutRequestedForRound) {
            timeoutRequestedForRound = null
        }
        syncCountdown(match)
        syncMatchClocks(match, userId)
        scheduleStuckRoundNudge(match, userId)
    }

    private fun cancelInFlightSubmit(clearPendingUi: Boolean = false) {
        submitGeneration++
        submitWatchdogJob?.cancel()
        submitWatchdogJob = null
        activeSubmitJob?.cancel()
        activeSubmitJob = null
        if (clearPendingUi) {
            _uiState.update {
                it.copy(isSubmitting = false, pendingMove = null)
            }
        }
    }

    private fun isSubmitForOpenRound(roundNumber: Int): Boolean =
        _uiState.value.match?.openRound()?.roundNumber == roundNumber

    /** When both players submitted but the server did not resolve, nudge timeout/recovery. */
    private fun scheduleStuckRoundNudge(match: Match?, userId: String?) {
        stuckRoundNudgeJob?.cancel()
        val openRound = match?.openRound() ?: return
        if (match.status != MatchStatus.ACTIVE || userId == null) return
        if (!openRound.hasSubmittedFor(userId, match.player1)) return
        if (!openRound.opponentHasSubmittedFor(userId, match.player1)) return

        val roundNumber = openRound.roundNumber
        stuckRoundNudgeJob = viewModelScope.launch {
            delay(2_000)
            val state = _uiState.value
            val currentOpen = state.match?.openRound() ?: return@launch
            if (currentOpen.roundNumber != roundNumber) return@launch
            if (!state.hasSubmittedMove || !state.opponentHasSubmitted) return@launch
            runCatching { syncMatchFromServer() }
            val afterSync = _uiState.value
            val stillOpen = afterSync.match?.openRound() ?: return@launch
            if (stillOpen.roundNumber != roundNumber) return@launch
            if (!afterSync.hasSubmittedMove || !afterSync.opponentHasSubmitted) return@launch
            requestTimeoutResolution(requireNotNull(afterSync.match), roundNumber)
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
            append(open?.player1Submitted)
            append('|')
            append(open?.player2Submitted)
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
        val serverSubmitted = openRound.hasSubmittedFor(userId, match.player1)
        val opponentSubmitted = openRound.opponentHasSubmittedFor(userId, match.player1)
        val fingerprint = buildString {
            append(match.clocksUpdatedAt)
            append('|')
            append(match.player1ClockMs)
            append('|')
            append(match.player2ClockMs)
            append('|')
            append(serverSubmitted)
            append('|')
            append(opponentSubmitted)
        }
        if (fingerprint != lastClockFingerprint) {
            lastClockFingerprint = fingerprint
            resyncClockAnchor(match, userId, myRunning = !serverSubmitted, oppRunning = !opponentSubmitted)
            _uiState.update {
                it.copy(opponentHasSubmitted = opponentSubmitted || !clockSyncOppRunning)
            }
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
                val opponentSubmittedNow = activeMatch.openRound()?.let { open ->
                    open.opponentHasSubmittedFor(userId, activeMatch.player1)
                } == true
                _uiState.update {
                    it.copy(
                        myClockSeconds = mySeconds,
                        opponentClockSeconds = oppSeconds,
                        opponentHasSubmitted = opponentSubmittedNow || !clockSyncOppRunning,
                    )
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
                        val matchClockWillResolve = !state.serverMoveSubmitted && state.myClockSeconds == 0
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
            val stillOnSameRound = state.match?.openRound()?.roundNumber == roundNumber
            if (!stillOnSameRound && state.error == null) return
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
        val userId = state.userId
        val match = state.match
        val openRound = match?.openRound()
        if (
            state.hasSubmittedMove ||
            state.isSubmitting ||
            state.pendingMove != null ||
            match?.status != MatchStatus.ACTIVE ||
            openRound == null ||
            userId == null ||
            openRound.hasSubmittedFor(userId, match.player1)
        ) {
            return
        }

        val roundNumber = openRound.roundNumber
        val generation = ++submitGeneration
        locallySubmittedRound = roundNumber
        lockedMoveRound = roundNumber
        submitWatchdogJob?.cancel()
        _uiState.update {
            it.copy(
                isSubmitting = true,
                hasSubmittedMove = true,
                pendingMove = move,
                lockedMove = move,
                error = null,
            )
        }

        activeSubmitJob?.cancel()
        activeSubmitJob = viewModelScope.launch {
            val serverSyncJob = launch {
                while (generation == submitGeneration) {
                    delay(400)
                    runCatching { syncMatchFromServer() }
                    if (!_uiState.value.isSubmitting && _uiState.value.hasSubmittedMove) break
                }
            }
            submitWatchdogJob = launch {
                delay(SUBMIT_STUCK_WATCHDOG_MS)
                if (generation != submitGeneration) return@launch
                if (!isSubmitForOpenRound(roundNumber)) return@launch
                revertMoveIfServerMissing(
                    generation,
                    roundNumber,
                ) { "Move is taking too long to reach the server. Tap a move to try again." }
            }
            var submitError: String? = null
            try {
                if (!isSubmitForOpenRound(roundNumber)) return@launch
                withTimeout(SUBMIT_MOVE_TIMEOUT_MS) {
                    matchRepository.submitMove(matchId, move, roundNumber)
                }
            } catch (e: TimeoutCancellationException) {
                submitError = "Connection timed out. Check your network and tap a move to try again."
            } catch (e: CancellationException) {
                if (generation == submitGeneration) {
                    submitError = "Move was interrupted. Tap a move to try again."
                }
            } catch (e: Exception) {
                if (!isIgnorableFirestoreRace(e)) {
                    submitError = GameFunctions.toSubmitErrorMessage(e)
                        ?: userFacingGameError(e, "Failed to submit move")
                }
            } finally {
                withContext(NonCancellable) {
                    submitWatchdogJob?.cancel()
                    serverSyncJob.cancel()
                    if (generation != submitGeneration) return@withContext
                    if (!isSubmitForOpenRound(roundNumber)) {
                        abandonStaleSubmitUi()
                        return@withContext
                    }
                    revertMoveIfServerMissing(
                        generation,
                        roundNumber,
                        confirmedMove = move,
                    ) {
                        submitError ?: "Move did not reach the server. Tap a move to try again."
                    }
                }
            }
        }
    }

    private fun abandonStaleSubmitUi() {
        locallySubmittedRound = null
        lockedMoveRound = null
        val userId = authRepository.currentUserId
        val match = _uiState.value.match
        val openRound = match?.openRound()
        val serverSubmitted = userId != null && match != null && openRound != null &&
            openRound.hasSubmittedFor(userId, match.player1)
        _uiState.update {
            it.copy(
                isSubmitting = false,
                pendingMove = null,
                hasSubmittedMove = serverSubmitted,
                serverMoveSubmitted = serverSubmitted,
                lockedMove = if (serverSubmitted) it.lockedMove else null,
            )
        }
    }

    private suspend fun confirmMoveOnServer(roundNumber: Int): Match? {
        val userId = authRepository.currentUserId ?: return null
        repeat(SUBMIT_CONFIRM_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(SUBMIT_CONFIRM_DELAY_MS)
            runCatching { syncMatchFromServer() }
            val match = _uiState.value.match ?: return@repeat
            if (match.openRound()?.roundNumber != roundNumber) return null
            if (match.hasSubmittedInRound(userId, roundNumber)) {
                return match
            }
        }
        return null
    }

    private suspend fun revertMoveIfServerMissing(
        generation: Int,
        roundNumber: Int,
        confirmedMove: Move? = null,
        errorMessage: () -> String,
    ) {
        if (generation != submitGeneration) return
        if (!isSubmitForOpenRound(roundNumber)) {
            abandonStaleSubmitUi()
            return
        }
        val confirmedMatch = confirmMoveOnServer(roundNumber)
        if (confirmedMatch != null) {
            locallySubmittedRound = roundNumber
            lockedMoveRound = roundNumber
            _uiState.update {
                it.copy(
                    match = confirmedMatch,
                    hasSubmittedMove = true,
                    serverMoveSubmitted = true,
                    lockedMove = confirmedMove ?: it.lockedMove ?: it.pendingMove,
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
                serverMoveSubmitted = false,
                lockedMove = null,
                isSubmitting = false,
                pendingMove = null,
                error = errorMessage(),
            )
        }
        syncMatchClocks(_uiState.value.match, authRepository.currentUserId)
    }

    override fun onCleared() {
        observeJob?.cancel()
        countdownJob?.cancel()
        clockJob?.cancel()
        resolvingRetryJob?.cancel()
        stuckRoundNudgeJob?.cancel()
        cancelInFlightSubmit()
        super.onCleared()
    }

    companion object {
        private const val SUBMIT_MOVE_TIMEOUT_MS = 15_000L
        private const val SUBMIT_STUCK_WATCHDOG_MS = 18_000L
        private const val SUBMIT_CONFIRM_ATTEMPTS = 6
        private const val SUBMIT_CONFIRM_DELAY_MS = 400L

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
