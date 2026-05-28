package com.rpsonline.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.repository.AppUpdateRepository
import com.rpsonline.app.data.update.ReleaseChangelog
import com.rpsonline.app.data.update.ReleaseChangelogEntry
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
    val isLoadingMore: Boolean = false,
    val entries: List<ReleaseChangelogEntry> = emptyList(),
    val hasMore: Boolean = true,
    val error: String? = null,
)

class ChangelogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChangelogUiState())
    val uiState: StateFlow<ChangelogUiState> = _uiState.asStateFlow()

    private var repository: AppUpdateRepository? = null
    private var currentPage = 0

    fun load(context: Context) {
        if (!NetworkUtils.isOnline(context)) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = "No internet connection. Connect to load release notes.",
                )
            }
            return
        }

        val repo = AppUpdateRepository(context.applicationContext)
        repository = repo
        currentPage = 0
        _uiState.update {
            it.copy(
                versionName = repo.currentVersionName(),
                isLoading = true,
                isLoadingMore = false,
                entries = emptyList(),
                hasMore = true,
                error = null,
            )
        }
        fetchPage(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || repository == null) return
        fetchPage(reset = false)
    }

    private fun fetchPage(reset: Boolean) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = reset,
                    isLoadingMore = !reset,
                    error = if (reset) null else it.error,
                )
            }
            val page = if (reset) 1 else currentPage + 1
            val result = withContext(Dispatchers.IO) {
                try {
                    repo.fetchChangelogPage(page)
                } catch (_: Exception) {
                    null
                }
            }
            if (result == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        // Keep pagination open on transient failures so scrolling can retry.
                        hasMore = if (reset) it.hasMore else true,
                        error = if (reset || it.entries.isEmpty()) {
                            "Couldn't load release notes."
                        } else {
                            "Couldn't load older release notes. Scroll to retry."
                        },
                    )
                }
                return@launch
            }

            currentPage = page
            _uiState.update { state ->
                val rawEntries = if (reset) result.entries else state.entries + result.entries
                val entries = ReleaseChangelog.mergeEntriesByDay(rawEntries)
                state.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    entries = entries,
                    hasMore = result.hasMore,
                    error = if (entries.isEmpty()) {
                        "Couldn't load release notes."
                    } else {
                        null
                    },
                )
            }
        }
    }
}
