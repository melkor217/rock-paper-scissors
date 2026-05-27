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
import kotlinx.coroutines.withTimeout
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
    val isInQueue: Boolean = false,
    val queueElapsedSeconds: Long = 0,
    val matchmakingError: String? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val matchRepository: MatchRepository = MatchRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var presenceJob: Job? = null
    private var profileJob: Job? = null
    private var activeMatchJob: Job? = null
    private var queueObserveJob: Job? = null
    private var queueTimerJob: Job? = null
    private var awaitingMatchFromQueue = false

    private val _navigateToGameMatchId = MutableStateFlow<String?>(null)
    val navigateToGameMatchId: StateFlow<String?> = _navigateToGameMatchId.asStateFlow()

    init {
        viewModelScope.launch {
            presenceRepository.observeOnlineCount().collect { count ->
                _uiState.update { it.copy(onlinePlayerCount = count) }
            }
        }
        viewModelScope.launch {
            authRepository.authStateFlow().collect { user ->
                profileJob?.cancel()
                profileJob = null
                if (user == null) {
                    stopPresenceHeartbeat()
                    activeMatchJob?.cancel()
                    activeMatchJob = null
                    queueObserveJob?.cancel()
                    queueObserveJob = null
                    stopQueueTimer()
                    awaitingMatchFromQueue = false
                    _navigateToGameMatchId.value = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = null,
                            onlinePlayerCount = null,
                            activeMatchId = null,
                            isInQueue = false,
                            queueElapsedSeconds = 0,
                            matchmakingError = null,
                        )
                    }
                } else {
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
        if (_uiState.value.isInQueue || _uiState.value.activeMatchId != null) return
        MatchModePreferences(context).set(matchModes)
        awaitingMatchFromQueue = true
        _uiState.update { it.copy(selectedMatchModes = matchModes, matchmakingError = null) }
        viewModelScope.launch {
            try {
                val immediateMatchId = matchRepository.joinQueue(matchModes)
                if (immediateMatchId != null) {
                    awaitingMatchFromQueue = false
                    _navigateToGameMatchId.value = immediateMatchId
                    return@launch
                }
            } catch (e: Exception) {
                awaitingMatchFromQueue = false
                _uiState.update {
                    it.copy(matchmakingError = e.message ?: "Matchmaking failed")
                }
            }
        }
    }

    fun leaveQueue() {
        awaitingMatchFromQueue = false
        viewModelScope.launch {
            try {
                matchRepository.leaveQueue()
            } catch (_: Exception) {
            } finally {
                _uiState.update {
                    it.copy(isInQueue = false, queueElapsedSeconds = 0, matchmakingError = null)
                }
            }
        }
    }

    fun consumeNavigateToGameMatch() {
        _navigateToGameMatchId.value = null
    }

    private fun observeQueue() {
        MatchSessionMonitor.ensureStarted()
        queueObserveJob?.cancel()
        queueObserveJob = viewModelScope.launch {
            MatchSessionMonitor.queueJoinedAtMs.collect { joinedAtMs ->
                if (joinedAtMs == null) {
                    stopQueueTimer()
                    if (awaitingMatchFromQueue || _navigateToGameMatchId.value != null) {
                        return@collect
                    }
                    _uiState.update {
                        it.copy(isInQueue = false, queueElapsedSeconds = 0)
                    }
                } else {
                    awaitingMatchFromQueue = true
                    _uiState.update { it.copy(isInQueue = true, matchmakingError = null) }
                    startQueueTimer(joinedAtMs)
                }
            }
        }
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
                val shouldAutoNavigate = isActive &&
                    (awaitingMatchFromQueue || _uiState.value.isInQueue)

                if (shouldAutoNavigate) {
                    val matchId = match?.id ?: return@collect
                    awaitingMatchFromQueue = false
                    stopQueueTimer()
                    _navigateToGameMatchId.value = matchId
                    _uiState.update {
                        it.copy(
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
        viewModelScope.launch {
            val uid = authRepository.currentUserId ?: return@launch
            userRepository.getUserProfile(uid)?.let { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
        refresh()
    }

    fun loadMatchModePreferences(context: Context) {
        val modes = MatchModePreferences(context).get()
        _uiState.update { it.copy(selectedMatchModes = modes) }
    }

    fun toggleMatchMode(context: Context, mode: MatchMode) {
        if (_uiState.value.isInQueue) return
        val current = _uiState.value.selectedMatchModes
        val updated = MatchMode.toggleInSelection(current, mode)
        MatchModePreferences(context).set(updated)
        _uiState.update { it.copy(selectedMatchModes = updated) }
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.currentUser ?: return@launch
            refresh(user)
        }
    }

    private suspend fun refresh(user: FirebaseUser) {
        val hadProfile = _uiState.value.profile != null
        if (!hadProfile) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        try {
            withTimeout(10_000) {
                authRepository.ensureUserProfile(
                    uid = user.uid,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString(),
                )
            }
            _uiState.update { it.copy(isLoading = false, error = null) }
            startPresenceHeartbeat(user.uid)
        } catch (e: TimeoutCancellationException) {
            stopPresenceHeartbeat()
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
            stopPresenceHeartbeat()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: if (!hadProfile) "Could not load profile." else null,
                )
            }
        }
    }

    private fun startPresenceHeartbeat(uid: String) {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            presenceRepository.touchPresence(uid)
            while (isActive) {
                delay(PresenceRepository.HEARTBEAT_INTERVAL_MS)
                presenceRepository.touchPresence(uid)
            }
        }
    }

    private fun stopPresenceHeartbeat() {
        presenceJob?.cancel()
        presenceJob = null
    }

    fun signOut(context: Context) {
        awaitingMatchFromQueue = false
        _navigateToGameMatchId.value = null
        viewModelScope.launch {
            stopPresenceHeartbeat()
            try {
                matchRepository.leaveQueue()
            } catch (_: Exception) {
            }
            authRepository.currentUserId?.let { uid ->
                presenceRepository.clearPresence(uid)
            }
            authRepository.signOut(context)
            _uiState.update {
                HomeUiState(
                    isLoading = false,
                    profile = null,
                    onlinePlayerCount = null,
                )
            }
        }
    }

    override fun onCleared() {
        profileJob?.cancel()
        activeMatchJob?.cancel()
        queueObserveJob?.cancel()
        stopQueueTimer()
        stopPresenceHeartbeat()
        super.onCleared()
    }
}
