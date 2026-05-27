package com.rpsonline.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.domain.DisplayNames
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.ui.components.AppUpdateDialogs
import com.rpsonline.app.ui.components.PlayersOnlineLabel
import com.rpsonline.app.ui.components.ProfileSummaryStatsCard
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.RpsCard
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.matchmaking.formatQueueTime
import com.rpsonline.app.ui.util.findActivity
import com.rpsonline.app.viewmodel.AppUpdateViewModel
import com.rpsonline.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onReconnectToGame: (String) -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onChangelog: () -> Unit,
    autoStartMatchModes: Set<MatchMode>? = null,
    viewModel: HomeViewModel = viewModel(),
    updateViewModel: AppUpdateViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        viewModel.onHomeVisible(context)
        updateViewModel.onScreenVisible(context)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    var autoMatchmakingStarted by remember(autoStartMatchModes) { mutableStateOf(false) }
    LaunchedEffect(autoStartMatchModes) {
        if (autoStartMatchModes != null && !autoMatchmakingStarted) {
            autoMatchmakingStarted = true
            viewModel.startMatchmaking(context, autoStartMatchModes)
        }
    }

    AppUpdateDialogs(
        updateState = updateState,
        activity = activity,
        viewModel = updateViewModel,
    )

    Column(
        modifier = Modifier.rpsScreenPadding(),
    ) {
        if (uiState.isLoading && uiState.profile == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RpsLoadingColumn(message = "Loading profile…")
            }
            return
        }

        if (uiState.profile == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = uiState.error ?: "Could not load your profile.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { viewModel.refresh() }) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.signOut(context) }) {
                    Text("Sign Out")
                }
            }
            return
        }

        val profile = uiState.profile
        val selectedModes = uiState.selectedMatchModes
        val activeMatchId = uiState.activeMatchId
        val matchModesLocked = uiState.isInQueue

        Text(
            text = "Welcome, ${profile?.displayName ?: DisplayNames.DEFAULT}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        ProfileSummaryStatsCard(
            elo = profile?.elo ?: 1000,
            wins = profile?.wins ?: 0,
            losses = profile?.losses ?: 0,
            draws = profile?.draws ?: 0,
            roundsWon = profile?.roundsWon ?: 0,
            roundsLost = profile?.roundsLost ?: 0,
            roundsDraw = profile?.roundsDraw ?: 0,
            throwsRock = profile?.throwsRock ?: 0,
            throwsPaper = profile?.throwsPaper ?: 0,
            throwsScissors = profile?.throwsScissors ?: 0,
            showHeader = true,
            showChevron = true,
            onClick = onProfile,
        )

        Spacer(modifier = Modifier.height(16.dp))

        uiState.onlinePlayerCount?.let { count ->
            PlayersOnlineLabel(
                count = count,
                modifier = Modifier.fillMaxWidth(),
                emphasized = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MatchMode.entries.forEach { mode ->
                    val selected = mode in selectedModes
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .toggleable(
                                value = selected,
                                enabled = !matchModesLocked,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.toggleMatchMode(context, mode) },
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = !matchModesLocked,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.outline,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                disabledUncheckedColor = MaterialTheme.colorScheme.outline,
                                disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            ),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (matchModesLocked) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        uiState.matchmakingError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isInQueue && activeMatchId == null) {
            HomeQueueStatusCard(queueElapsedSeconds = uiState.queueElapsedSeconds)
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            activeMatchId != null -> {
                Button(
                    onClick = { onReconnectToGame(activeMatchId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                ) {
                    Text(
                        text = "Reconnect to Game",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            uiState.isInQueue -> {
                OutlinedButton(
                    onClick = { viewModel.leaveQueue() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Leave Queue")
                }
            }
            else -> {
                Button(
                    onClick = { viewModel.startMatchmaking(context, selectedModes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                ) {
                    Text(
                        text = "Find Match",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onLeaderboard,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Leaderboard")
        }
        Spacer(modifier = Modifier.height(8.dp))

        HomeAppInfoFooter(
            versionName = updateState.versionName,
            updatesEnabled = BuildConfig.GITHUB_UPDATES_ENABLED,
            availableUpdate = updateState.availableUpdate,
            isCheckingForUpdate = updateState.isCheckingForUpdate,
            isDownloadingUpdate = updateState.isDownloadingUpdate,
            updateMessage = updateState.updateMessage,
            onCheckForUpdate = { updateViewModel.checkForUpdate(context) },
            onInstallUpdate = {
                activity?.let { updateViewModel.downloadAndInstallUpdate(it) }
                    ?: updateViewModel.showUpdatePrompt()
            },
            onVersionClick = onChangelog,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.signOut(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign Out")
        }
    }
}

@Composable
private fun HomeQueueStatusCard(queueElapsedSeconds: Long) {
    RpsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "In queue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatQueueTime(queueElapsedSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Finding an opponent…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
