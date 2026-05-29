package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.preferences.MatchModePreferences
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.data.repository.PresenceRepository
import com.rpsonline.app.data.repository.UserRepository
import com.rpsonline.app.domain.MatchMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val onlinePlayerCount: Int? = null,
    val selectedMatchModes: Set<MatchMode> = MatchMode.DEFAULT_SELECTION,
    val activeMatchId: String? = null,
    val isJoiningQueue: Boolean = false,
    val isInQueue: Boolean = false,
    val queueElapsedSeconds: Long = 0,
    val matchmakingError: String? = null,
    val error: String? = null,
    val isSigningOut: Boolean = false,
)

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val matchRepository: MatchRepository = MatchRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var onlineCountJob: Job? = null
    private var profileJob: Job? = null
    private var activeMatchJob: Job? = null
    private var queueObserveJob: Job? = null
    private var queueTimerJob: Job? = null
    private var refreshJob: Job? = null
    private var awaitingMatchFromQueue = false
    private var awaitingMatchStartedAtMs: Long? = null
    private var matchmakingGeneration = 0

    companion object {
        private const val MATCH_ASSIGNMENT_GRACE_MS = 30_000L
        private const val MATCHMAKING_WATCHDOG_MS = 45_000L
    }

    val navigateToGameMatchId: StateFlow<String?> = MatchSessionMonitor.pendingGameNavigationMatchId

    init {
        startOnlineCountObserver()
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                profileJob?.cancel()
                profileJob = null
                if (user == null) {
                    refreshJob?.cancel()
                    refreshJob = null
                    stopOnlineCountObserver()
                    activeMatchJob?.cancel()
                    activeMatchJob = null
                    queueObserveJob?.cancel()
                    queueObserveJob = null
                    stopQueueTimer()
                    awaitingMatchFromQueue = false
                    MatchSessionMonitor.consumeGameNavigation()
                    MatchSessionMonitor.setMatchmakingInProgress(false)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = null,
                            onlinePlayerCount = null,
                            activeMatchId = null,
                            isJoiningQueue = false,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                            matchmakingError = null,
                            isSigningOut = false,
                            error = null,
                        )
                    }
                } else {
                    startOnlineCountObserver()
                    viewModelScope.launch {
                        runCatching {
                            presenceRepository.touchPresence(user.uid, forceAuthRefresh = true)
                        }
                        refreshOnlinePlayerCount()
                    }
                    if (_uiState.value.profile == null) {
                        _uiState.update {
                            it.copy(profile = authRepository.fallbackProfile(user), isLoading = false)
                        }
                    }
                    profileJob = viewModelScope.launch {
                        userRepository.observeUserProfile(user.uid).collect { profile ->
                            if (profile != null) {
                                _uiState.update { it.copy(profile = profile, isLoading = false, error = null) }
                            }
                        }
                    }
                    refresh(user)
                    observeActiveMatch()
                    observeQueue()
                }
            }
        }
    }

    fun startMatchmaking(context: Context, matchModes: Set<MatchMode>) {
        if (
            _uiState.value.isJoiningQueue ||
            _uiState.value.isInQueue ||
            _uiState.value.activeMatchId != null
        ) {
            return
        }
        val generation = ++matchmakingGeneration
        refreshJob?.cancel()
        MatchSessionMonitor.setMatchmakingInProgress(true)
        MatchModePreferences(context).set(matchModes)
        awaitingMatchFromQueue = true
        awaitingMatchStartedAtMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                selectedMatchModes = matchModes,
                isJoiningQueue = true,
                isInQueue = false,
                queueElapsedSeconds = 0,
                matchmakingError = null,
            )
        }
        viewModelScope.launch {
            val watchdog = launch {
                delay(MATCHMAKING_WATCHDOG_MS)
                if (generation != matchmakingGeneration) return@launch
                if (!_uiState.value.isJoiningQueue) return@launch
                failMatchmaking(
                    generation = generation,
                    message = "Matchmaking timed out. Check your connection and try again.",
                )
            }
            try {
                withContext(Dispatchers.IO) {
                    if (!isFirebaseAvailableForQueueAction("join the queue", generation)) return@withContext
                    val profile = awaitUserProfileReady()
                    if (generation != matchmakingGeneration) return@withContext

                    val alreadyQueuedAtMs = runCatching { matchRepository.getQueueJoinedAtMs() }.getOrNull()
                    if (generation != matchmakingGeneration) return@withContext
                    if (alreadyQueuedAtMs != null) {
                        enterConfirmedQueue(alreadyQueuedAtMs)
                        return@withContext
                    }
                    val joinResult = matchRepository.joinQueue(matchModes, profile)
                    if (generation != matchmakingGeneration) {
                        if (joinResult.immediateMatchId == null) {
                            authRepository.currentUserId?.let { matchRepository.leaveQueueBestEffort(it) }
                        }
                        return@withContext
                    }
                    if (joinResult.immediateMatchId != null) {
                        awaitingMatchFromQueue = false
                        awaitingMatchStartedAtMs = null
                        stopQueueTimer()
                        _uiState.update {
                            it.copy(
                                isJoiningQueue = false,
                                isInQueue = false,
                                queueElapsedSeconds = 0,
                                matchmakingError = null,
                            )
                        }
                        MatchSessionMonitor.requestGameNavigation(joinResult.immediateMatchId)
                        return@withContext
                    }
                    val joinedAtMs = joinResult.clientJoinedAtMs ?: System.currentTimeMillis()
                    enterConfirmedQueue(joinedAtMs)
                }
                if (generation == matchmakingGeneration && _uiState.value.isInQueue) {
                    viewModelScope.launch {
                        val serverJoinedAtMs = matchRepository.awaitQueueJoinedAtFromServer(timeoutMs = 30_000)
                        if (generation != matchmakingGeneration) return@launch
                        if (serverJoinedAtMs != null && _uiState.value.isInQueue) {
                            enterConfirmedQueue(serverJoinedAtMs)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (generation != matchmakingGeneration) return@launch
                failMatchmaking(
                    generation = generation,
                    message = "Could not join the matchmaking queue in time. Check your connection and try again.",
                )
            } catch (e: Exception) {
                if (generation != matchmakingGeneration) return@launch
                val message = when {
                    e.message?.contains("profile", ignoreCase = true) == true -> e.message!!
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                        "Could not write to the matchmaking queue. Check Firebase App Check (Monitoring, not Enforced) and try again."
                    e.message?.contains("Timed out", ignoreCase = true) == true ||
                        e.message?.contains("server", ignoreCase = true) == true ->
                        "Could not join the matchmaking queue in time. Check your connection and try again."
                    !e.message.isNullOrBlank() -> e.message!!
                    else -> "Matchmaking failed. Check your connection and try again."
                }
                failMatchmaking(generation, message)
            } finally {
                watchdog.cancel()
            }
        }
    }

    private fun failMatchmaking(generation: Int, message: String) {
        if (generation != matchmakingGeneration) return
        cleanupMatchmakingSession()
        _uiState.update {
            it.copy(
                isJoiningQueue = false,
                isInQueue = false,
                queueElapsedSeconds = 0,
                matchmakingError = message,
            )
        }
    }

    private fun cleanupMatchmakingSession() {
        MatchSessionMonitor.setMatchmakingInProgress(false)
        awaitingMatchFromQueue = false
        awaitingMatchStartedAtMs = null
        stopQueueTimer()
        MatchSessionMonitor.clearQueueState()
        authRepository.currentUserId?.let { uid ->
            matchRepository.leaveQueueBestEffort(uid)
        }
    }

    /** Ensures the Firestore user doc exists before queue writes (required by security rules). */
    private suspend fun awaitUserProfileReady(): UserProfile {
        val user = authRepository.currentUser ?: error("Not signed in")
        authRepository.queueReadyProfile(user.uid)?.let { return it }
        return authRepository.ensureUserProfile(
            uid = user.uid,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString(),
        )
    }

    fun leaveQueue() {
        val userId = authRepository.currentUserId
        resetMatchmakingLocalState()
        _uiState.update {
            it.copy(
                isJoiningQueue = false,
                isInQueue = false,
                queueElapsedSeconds = 0,
                matchmakingError = null,
            )
        }
        userId?.let { uid ->
            viewModelScope.launch {
                runCatching { matchRepository.leaveQueueForUser(uid) }
            }
        }
    }

    fun consumeNavigateToGameMatch() {
        MatchSessionMonitor.consumeGameNavigation()
        MatchSessionMonitor.setMatchmakingInProgress(false)
    }

    private fun observeQueue() {
        MatchSessionMonitor.ensureStarted()
        queueObserveJob?.cancel()
        queueObserveJob = viewModelScope.launch {
            MatchSessionMonitor.queueJoinedAtMs.collect { joinedAtMs ->
                if (joinedAtMs == null) {
                    if (_uiState.value.isJoiningQueue || MatchSessionMonitor.isQueueEntryPending()) {
                        return@collect
                    }
                    stopQueueTimer()
                    // Queue doc may disappear slightly before activeMatch arrives; keep a short handoff window.
                    val withinAssignmentGrace = awaitingMatchFromQueue &&
                        ((awaitingMatchStartedAtMs?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE) <= MATCH_ASSIGNMENT_GRACE_MS)
                    if (!withinAssignmentGrace) {
                        awaitingMatchFromQueue = false
                        awaitingMatchStartedAtMs = null
                    }
                    _uiState.update {
                        it.copy(
                            isJoiningQueue = false,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                        )
                    }
                } else {
                    enterConfirmedQueue(joinedAtMs)
                }
            }
        }
    }

    private fun enterConfirmedQueue(joinedAtMs: Long) {
        awaitingMatchFromQueue = true
        awaitingMatchStartedAtMs = System.currentTimeMillis()
        MatchSessionMonitor.confirmQueueJoinedAt(joinedAtMs)
        _uiState.update {
            it.copy(
                isJoiningQueue = false,
                isInQueue = true,
                matchmakingError = null,
            )
        }
        startQueueTimer(joinedAtMs)
    }

    private fun startQueueTimer(joinedAtMs: Long) {
        queueTimerJob?.cancel()
        queueTimerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - joinedAtMs) / 1_000).coerceAtLeast(0)
                _uiState.update { it.copy(queueElapsedSeconds = elapsed, isInQueue = true) }
                delay(1_000)
            }
        }
    }

    private fun stopQueueTimer() {
        queueTimerJob?.cancel()
        queueTimerJob = null
    }

    private fun observeActiveMatch() {
        MatchSessionMonitor.ensureStarted()
        activeMatchJob?.cancel()
        activeMatchJob = viewModelScope.launch {
            MatchSessionMonitor.activeMatch.collect { match ->
                val isActive = match?.status == MatchStatus.ACTIVE
                val shouldAutoNavigate = isActive && match != null && (
                    MatchSessionMonitor.isMatchmakingInProgress() ||
                    awaitingMatchFromQueue ||
                    _uiState.value.isInQueue ||
                    _uiState.value.isJoiningQueue
                )

                if (shouldAutoNavigate) {
                    val matchId = match.id
                    awaitingMatchFromQueue = false
                    awaitingMatchStartedAtMs = null
                    stopQueueTimer()
                    MatchSessionMonitor.requestGameNavigation(matchId)
                    _uiState.update {
                        it.copy(
                            isJoiningQueue = false,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                            matchmakingError = null,
                        )
                    }
                    return@collect
                }

                val activeMatchId = match?.id.takeIf { isActive }
                _uiState.update { it.copy(activeMatchId = activeMatchId) }
            }
        }
    }

    fun onHomeVisible(context: Context) {
        loadMatchModePreferences(context)
        startOnlineCountObserver()
        refreshOnlinePlayerCount()
        viewModelScope.launch {
            runCatching { MatchSessionMonitor.refreshOnResume() }
            val uid = authRepository.currentUserId ?: return@launch
            runCatching { presenceRepository.touchPresence(uid) }
            userRepository.getUserProfile(uid)?.let { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
            // Reconcile queue state on resume in case listener updates were missed while away.
            val joinedAtMs = runCatching { matchRepository.getQueueJoinedAtMs() }.getOrNull()
            if (joinedAtMs == null) {
                val monitorJoinedAtMs = MatchSessionMonitor.queueJoinedAtMs.value
                if (monitorJoinedAtMs != null) {
                    enterConfirmedQueue(monitorJoinedAtMs)
                } else if (
                    MatchSessionMonitor.isMatchmakingInProgress() ||
                    MatchSessionMonitor.isQueueEntryPending()
                ) {
                    awaitingMatchFromQueue = true
                    if (awaitingMatchStartedAtMs == null) {
                        awaitingMatchStartedAtMs = System.currentTimeMillis()
                    }
                    _uiState.update {
                        it.copy(
                            isJoiningQueue = true,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                            matchmakingError = null,
                        )
                    }
                } else {
                    awaitingMatchFromQueue = false
                    awaitingMatchStartedAtMs = null
                    stopQueueTimer()
                    _uiState.update {
                        it.copy(
                            isJoiningQueue = false,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                            matchmakingError = null,
                        )
                    }
                }
            } else {
                enterConfirmedQueue(joinedAtMs)
            }
        }
        refresh()
    }

    fun loadMatchModePreferences(context: Context) {
        val modes = MatchModePreferences(context).get()
        _uiState.update { it.copy(selectedMatchModes = modes) }
    }

    fun toggleMatchMode(context: Context, mode: MatchMode) {
        if (_uiState.value.isJoiningQueue || _uiState.value.isInQueue) return
        val current = _uiState.value.selectedMatchModes
        val updated = MatchMode.toggleInSelection(current, mode)
        MatchModePreferences(context).set(updated)
        _uiState.update { it.copy(selectedMatchModes = updated) }
    }

    fun refresh() {
        if (_uiState.value.isSigningOut) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val user = authRepository.currentUser ?: return@launch
            refresh(user)
        }
    }

    private suspend fun refresh(user: FirebaseUser) {
        if (_uiState.value.isSigningOut || authRepository.currentUser == null) return
        val hadProfile = _uiState.value.profile != null
        if (!hadProfile) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        try {
            withTimeout(10_000) {
                if (MatchSessionMonitor.isMatchmakingInProgress()) {
                    authRepository.waitUntilQueueReadyProfile(user.uid, timeoutMs = 8_000)
                } else if (
                    !authRepository.waitUntilQueueReadyProfile(user.uid, timeoutMs = 4_000)
                ) {
                    authRepository.ensureUserProfile(
                        uid = user.uid,
                        displayName = user.displayName,
                        photoUrl = user.photoUrl?.toString(),
                    )
                }
            }
            _uiState.update { it.copy(isLoading = false, error = null) }
        } catch (e: TimeoutCancellationException) {
            if (_uiState.value.isSigningOut || authRepository.currentUser == null) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (it.profile == null) {
                        "No internet connection. Connect to sync your profile."
                    } else {
                        null
                    },
                )
            }
        } catch (e: Exception) {
            if (_uiState.value.isSigningOut || authRepository.currentUser == null) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: if (!hadProfile) "Could not load profile." else null,
                )
            }
        }
    }

    private fun startOnlineCountObserver() {
        onlineCountJob?.cancel()
        onlineCountJob = viewModelScope.launch {
            presenceRepository.observeOnlineCount().collect { count ->
                _uiState.update { it.copy(onlinePlayerCount = count) }
            }
        }
    }

    private fun stopOnlineCountObserver() {
        onlineCountJob?.cancel()
        onlineCountJob = null
    }

    private fun refreshOnlinePlayerCount() {
        viewModelScope.launch {
            runCatching { presenceRepository.fetchOnlineCountFromServer() }
                .onSuccess { count ->
                    _uiState.update { it.copy(onlinePlayerCount = count) }
                }
        }
    }

    fun signOut(context: Context) {
        val uid = authRepository.currentUserId
        refreshJob?.cancel()
        resetMatchmakingLocalState()
        MatchSessionMonitor.consumeGameNavigation()
        MatchSessionMonitor.setMatchmakingInProgress(false)
        stopOnlineCountObserver()
        _uiState.update {
            it.copy(isSigningOut = true, error = null, matchmakingError = null)
        }
        viewModelScope.launch {
            if (uid != null) {
                withTimeoutOrNull(5_000) {
                    runCatching { matchRepository.leaveQueueForUser(uid) }
                }
                runCatching { presenceRepository.clearPresence(uid) }
            }
            authRepository.signOut(context)
        }
    }

    private fun resetMatchmakingLocalState() {
        matchmakingGeneration++
        cleanupMatchmakingSession()
    }

    override fun onCleared() {
        profileJob?.cancel()
        refreshJob?.cancel()
        activeMatchJob?.cancel()
        queueObserveJob?.cancel()
        stopQueueTimer()
        stopOnlineCountObserver()
        super.onCleared()
    }

    private suspend fun isFirebaseAvailableForQueueAction(action: String, generation: Int): Boolean {
        if (authRepository.isFirebaseAvailable()) return true
        failMatchmaking(
            generation = generation,
            message = "Cannot $action right now. Firebase is unavailable.",
        )
        return false
    }
}
