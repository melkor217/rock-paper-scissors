package com.rpsonline.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.UserRepository
import com.rpsonline.app.domain.enrichMatchHistoryWithOpponentElos
import com.rpsonline.app.domain.DailyEloDelta
import com.rpsonline.app.domain.weeklyEloDailyDeltas
import com.rpsonline.app.domain.weeklyChartWindowStartMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isMatchHistoryLoading: Boolean = true,
    val isWeeklyChartLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val profile: UserProfile? = null,
    val matchHistory: List<MatchHistoryEntry> = emptyList(),
    val weeklyEloChart: List<DailyEloDelta> = emptyList(),
    val hasMoreMatches: Boolean = true,
    val isOwnProfile: Boolean = true,
    val error: String? = null,
)

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val matchRepository: MatchRepository = MatchRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var profileJob: Job? = null
    private var loadMoreJob: Job? = null
    private var loadedUserId: String? = null
    private var cachedViewerId: String? = null

    fun load(userId: String) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            userRepository.observeUserProfile(userId).collect { profile ->
                if (profile != null) {
                    _uiState.update { it.copy(profile = profile, error = null) }
                }
            }
        }
        loadJob = viewModelScope.launch {
            val switchingPlayer = loadedUserId != null && loadedUserId != userId
            val showFullScreenLoading = _uiState.value.profile == null || switchingPlayer
            if (switchingPlayer) {
                _uiState.update {
                    ProfileUiState(
                        isLoading = true,
                        isMatchHistoryLoading = true,
                        isWeeklyChartLoading = true,
                        isOwnProfile = userId == authRepository.currentUserId,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = showFullScreenLoading,
                        isMatchHistoryLoading = true,
                        isWeeklyChartLoading = true,
                        error = null,
                    )
                }
            }
            try {
                val viewerId = authRepository.currentUserId
                    ?: throw IllegalStateException("Not signed in")
                val profile = userRepository.getUserProfile(userId)
                    ?: throw IllegalStateException("Player not found")
                val isOwnProfile = userId == viewerId
                cachedViewerId = viewerId
                val sinceMs = weeklyChartWindowStartMs()
                val weeklyMatches = if (isOwnProfile) {
                    matchRepository.getRecentMatchesForUserSince(
                        userId = viewerId,
                        sinceMs = sinceMs,
                    )
                } else {
                    matchRepository.getRecentSharedMatchesSince(
                        viewerId = viewerId,
                        opponentId = userId,
                        sinceMs = sinceMs,
                    )
                }
                val weeklyEloChart = weeklyEloDailyDeltas(
                    enrichMatchHistoryWithOpponentElos(
                        viewerId = userId,
                        myCurrentElo = profile.elo,
                        matches = weeklyMatches,
                    ),
                )
                val matches = if (isOwnProfile) {
                    matchRepository.getRecentMatchesForUser(
                        userId = viewerId,
                        limit = MATCH_HISTORY_PAGE_SIZE,
                    )
                } else {
                    matchRepository.getRecentSharedMatches(
                        viewerId = viewerId,
                        opponentId = userId,
                        limit = MATCH_HISTORY_PAGE_SIZE,
                    )
                }
                val historySubjectElo = if (isOwnProfile) {
                    profile.elo
                } else {
                    userRepository.getUserProfile(viewerId)?.elo ?: 1000
                }
                val history = enrichMatchHistoryWithOpponentElos(
                    viewerId = viewerId,
                    myCurrentElo = historySubjectElo,
                    matches = matches,
                )
                loadedUserId = userId
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isMatchHistoryLoading = false,
                        isWeeklyChartLoading = false,
                        isLoadingMore = false,
                        profile = profile,
                        matchHistory = history,
                        weeklyEloChart = weeklyEloChart,
                        hasMoreMatches = matches.size >= MATCH_HISTORY_PAGE_SIZE,
                        isOwnProfile = isOwnProfile,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isMatchHistoryLoading = false,
                        isWeeklyChartLoading = false,
                        isLoadingMore = false,
                        error = e.message ?: "Failed to load profile",
                    )
                }
            }
        }
    }

    fun loadMoreMatchHistory() {
        val userId = loadedUserId ?: return
        val currentViewerId = cachedViewerId ?: return
        val state = _uiState.value
        if (
            state.isMatchHistoryLoading ||
            state.isLoadingMore ||
            !state.hasMoreMatches ||
            state.profile == null
        ) {
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val profile = state.profile ?: return@launch
                val nextLimit = state.matchHistory.size + MATCH_HISTORY_PAGE_SIZE
                val matches = if (state.isOwnProfile) {
                    matchRepository.getRecentMatchesForUser(
                        userId = currentViewerId,
                        limit = nextLimit,
                    )
                } else {
                    matchRepository.getRecentSharedMatches(
                        viewerId = currentViewerId,
                        opponentId = userId,
                        limit = nextLimit,
                    )
                }
                val historySubjectElo = if (state.isOwnProfile) {
                    profile.elo
                } else {
                    userRepository.getUserProfile(currentViewerId)?.elo ?: 1000
                }
                val history = enrichMatchHistoryWithOpponentElos(
                    viewerId = currentViewerId,
                    myCurrentElo = historySubjectElo,
                    matches = matches,
                )
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        matchHistory = history,
                        hasMoreMatches = matches.size >= nextLimit,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        profileJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MATCH_HISTORY_PAGE_SIZE = 10
    }
}
