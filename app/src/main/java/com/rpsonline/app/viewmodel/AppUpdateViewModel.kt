package com.rpsonline.app.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.repository.AppUpdateRepository
import com.rpsonline.app.data.update.AppUpdateInfo
import com.rpsonline.app.ui.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateUiState(
    val versionName: String = "",
    val availableUpdate: AppUpdateInfo? = null,
    val dismissedUpdateTag: String? = null,
    val isCheckingForUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateMessage: String? = null,
)

class AppUpdateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun onScreenVisible(context: Context) {
        val repo = AppUpdateRepository(context.applicationContext)
        _uiState.update { it.copy(versionName = repo.currentVersionName()) }
        if (repo.updatesEnabled() && NetworkUtils.isOnline(context)) {
            checkForUpdate(repo)
        }
    }

    fun checkForUpdate(context: Context) {
        if (!NetworkUtils.isOnline(context)) {
            _uiState.update {
                it.copy(
                    isCheckingForUpdate = false,
                    updateMessage = "No internet connection. Connect to check for updates.",
                )
            }
            return
        }
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
}
