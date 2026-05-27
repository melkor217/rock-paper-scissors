package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.LeaderboardEntry
import com.google.firebase.firestore.DocumentSnapshot
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val isAppending: Boolean = false,
    val entries: List<LeaderboardEntry> = emptyList(),
    val currentUserId: String? = null,
    val error: String? = null,
    val hasMore: Boolean = true,
)

class LeaderboardViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LeaderboardUiState(currentUserId = authRepository.currentUserId),
    )
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var appendJob: Job? = null

    private var nextCursor: DocumentSnapshot? = null
    private var pendingEntries: List<LeaderboardEntry> = emptyList()
    private var hasMoreFromFirestore: Boolean = true

    private companion object {
        private const val PAGE_SIZE = 25L
    }

    private fun resetPagination() {
        nextCursor = null
        pendingEntries = emptyList()
        hasMoreFromFirestore = true
    }

    private fun hasMorePages(): Boolean =
        pendingEntries.isNotEmpty() || hasMoreFromFirestore

    private fun appendEntries(existing: List<LeaderboardEntry>, newEntries: List<LeaderboardEntry>): List<LeaderboardEntry> =
        (existing + newEntries).distinctBy { it.uid }

    fun load() {
        loadJob?.cancel()
        appendJob?.cancel()
        resetPagination()

        loadJob = viewModelScope.launch {
            val showFullScreenLoading = _uiState.value.entries.isEmpty()
            _uiState.update {
                it.copy(
                    isLoading = showFullScreenLoading,
                    isAppending = false,
                    error = null,
                    hasMore = true,
                )
            }
            try {
                val userId = authRepository.currentUserId
                val page = userRepository.getLeaderboardPage(
                    pageSize = PAGE_SIZE,
                    startAfter = null,
                )
                nextCursor = page.nextCursor
                pendingEntries = page.pendingEntries
                hasMoreFromFirestore = page.hasMoreFromFirestore
                val entries = page.entries
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        currentUserId = userId,
                        hasMore = hasMorePages(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAppending = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    fun loadMore() {
        // Guard against multiple in-flight requests and end-of-list.
        if (_uiState.value.isLoading || _uiState.value.isAppending || !_uiState.value.hasMore) return

        appendJob?.cancel()
        appendJob = viewModelScope.launch {
            _uiState.update { it.copy(isAppending = true, error = null) }
            try {
                if (!hasMorePages()) {
                    _uiState.update { it.copy(isAppending = false, hasMore = false) }
                    return@launch
                }

                val newEntries =
                    if (pendingEntries.isNotEmpty()) {
                        val chunk = pendingEntries.take(PAGE_SIZE.toInt())
                        pendingEntries = pendingEntries.drop(chunk.size)
                        chunk
                    } else {
                        val page = userRepository.getLeaderboardPage(
                            pageSize = PAGE_SIZE,
                            startAfter = nextCursor,
                        )
                        nextCursor = page.nextCursor
                        pendingEntries = page.pendingEntries
                        hasMoreFromFirestore = page.hasMoreFromFirestore
                        page.entries
                    }

                _uiState.update {
                    it.copy(
                        isAppending = false,
                        entries = appendEntries(it.entries, newEntries),
                        hasMore = hasMorePages(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAppending = false, error = e.message) }
            }
        }
    }
}
