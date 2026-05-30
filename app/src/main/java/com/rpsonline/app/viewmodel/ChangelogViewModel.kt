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
    private var rawReleaseEntries: List<ReleaseChangelogEntry> = emptyList()

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
        rawReleaseEntries = emptyList()
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
            rawReleaseEntries = if (reset) {
                result.entries
            } else {
                val seen = rawReleaseEntries.map { it.tag }.toSet()
                rawReleaseEntries + result.entries.filter { it.tag !in seen }
            }
            val entries = ReleaseChangelog.mergeEntriesByDay(rawReleaseEntries)
            _uiState.update { state ->
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

            if (result.hasMore && entries.size < MIN_ENTRIES_BEFORE_STOP_PREFETCH) {
                fetchPage(reset = false)
            }
        }
    }

    companion object {
        /** Prefetch until the list scrolls or GitHub runs out of pages. */
        private const val MIN_ENTRIES_BEFORE_STOP_PREFETCH = 12
    }
}
