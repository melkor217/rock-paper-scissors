package com.rpsonline.app.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rpsonline.app.data.model.LeaderboardEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AppUpdateRepository
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.PresenceRepository
import com.rpsonline.app.data.repository.UserRepository
import com.rpsonline.app.data.update.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val onlinePlayerCount: Int? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val error: String? = null,
    val versionName: String = "",
    val availableUpdate: AppUpdateInfo? = null,
    val dismissedUpdateTag: String? = null,
    val isCheckingForUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateMessage: String? = null,
)

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var presenceJob: Job? = null
    private var profileJob: Job? = null

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
                    _uiState.update {
                        it.copy(isLoading = false, profile = null, onlinePlayerCount = null)
                    }
                } else {
                    profileJob = viewModelScope.launch {
                        userRepository.observeUserProfile(user.uid).collect { profile ->
                            if (profile != null) {
                                _uiState.update { it.copy(profile = profile, isLoading = false) }
                            }
                        }
                    }
                    refresh(user)
                }
            }
        }
    }

    fun onHomeVisible(context: Context) {
        refresh()
        val repo = AppUpdateRepository(context.applicationContext)
        _uiState.update { it.copy(versionName = repo.currentVersionName()) }
        if (repo.updatesEnabled()) {
            checkForUpdate(repo)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.currentUser ?: return@launch
            refresh(user)
        }
    }

    private suspend fun refresh(user: FirebaseUser) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            authRepository.ensureUserProfile(
                uid = user.uid,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
            )
            val leaderboard = userRepository.getLeaderboard(limit = 10)
            _uiState.update {
                it.copy(isLoading = false, leaderboard = leaderboard)
            }
            startPresenceHeartbeat(user.uid)
        } catch (e: Exception) {
            stopPresenceHeartbeat()
            _uiState.update { it.copy(isLoading = false, error = e.message) }
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

    fun checkForUpdate(context: Context) {
        checkForUpdate(AppUpdateRepository(context.applicationContext))
    }

    private fun checkForUpdate(repo: AppUpdateRepository) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdate = true, updateMessage = null) }
            val update = withContext(Dispatchers.IO) { repo.fetchUpdateIfAvailable() }
            _uiState.update {
                it.copy(
                    isCheckingForUpdate = false,
                    availableUpdate = update,
                    updateMessage = when {
                        update != null -> null
                        repo.updatesEnabled() -> "You're on the latest version."
                        else -> null
                    },
                )
            }
        }
    }

    fun dismissUpdate() {
        val tag = _uiState.value.availableUpdate?.tag
        _uiState.update { it.copy(dismissedUpdateTag = tag) }
    }

    fun showUpdatePrompt() {
        _uiState.update { it.copy(dismissedUpdateTag = null) }
    }

    fun downloadAndInstallUpdate(activity: Activity) {
        val update = _uiState.value.availableUpdate ?: return
        val repo = AppUpdateRepository(activity.applicationContext)
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDownloadingUpdate = true, updateDownloadProgress = null, updateMessage = null)
            }
            try {
                repo.downloadAndInstall(activity, update) { read, total ->
                    val progress = if (total > 0L) read.toFloat() / total.toFloat() else null
                    _uiState.update { state -> state.copy(updateDownloadProgress = progress) }
                }
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateMessage = "Follow the system prompt to install the update.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateMessage = e.message ?: "Update failed",
                    )
                }
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            stopPresenceHeartbeat()
            authRepository.currentUserId?.let { uid ->
                presenceRepository.clearPresence(uid)
            }
            authRepository.signOut(context)
            _uiState.update {
                HomeUiState(
                    isLoading = false,
                    profile = null,
                    onlinePlayerCount = null,
                    leaderboard = emptyList(),
                )
            }
        }
    }

    override fun onCleared() {
        profileJob?.cancel()
        stopPresenceHeartbeat()
        super.onCleared()
    }
}
