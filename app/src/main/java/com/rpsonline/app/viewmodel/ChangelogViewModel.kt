package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.repository.AppUpdateRepository
import com.rpsonline.app.ui.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChangelogUiState(
    val versionName: String = "",
    val isLoading: Boolean = true,
    val releaseNotes: String? = null,
    val error: String? = null,
)

class ChangelogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChangelogUiState())
    val uiState: StateFlow<ChangelogUiState> = _uiState.asStateFlow()

    fun load(context: Context) {
        val repo = AppUpdateRepository(context.applicationContext)
        val versionName = repo.currentVersionName()
        _uiState.update {
            it.copy(
                versionName = versionName,
                isLoading = true,
                releaseNotes = null,
                error = null,
            )
        }
        if (!NetworkUtils.isOnline(context)) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "No internet connection. Connect to load release notes.",
                )
            }
            return
        }
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) {
                try {
                    repo.fetchInstalledReleaseNotes()
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    releaseNotes = notes,
                    error = if (notes.isNullOrBlank()) {
                        "Couldn't load release notes for v$versionName."
                    } else {
                        null
                    },
                )
            }
        }
    }
}
