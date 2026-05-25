package com.rpsonline.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.domain.DisplayNames
import com.rpsonline.app.ui.components.PlayersOnlineLabel
import com.rpsonline.app.ui.components.ThrowCountRow
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.ui.leaderboard.RpsPerWinLabel
import com.rpsonline.app.ui.leaderboard.throwsPerWin
import com.rpsonline.app.ui.util.findActivity
import com.rpsonline.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onFindMatch: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        viewModel.onHomeVisible(context)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val pendingUpdate = uiState.availableUpdate?.takeIf {
        it.tag != uiState.dismissedUpdateTag
    }
    if (pendingUpdate != null && activity != null && !uiState.isDownloadingUpdate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${pendingUpdate.versionLabel} is available on GitHub.")
                    pendingUpdate.releaseNotes?.let { notes ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notes.take(500),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.downloadAndInstallUpdate(activity) },
                ) {
                    Text("Download & install")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    Text("Later")
                }
            },
        )
    }

    if (uiState.isDownloadingUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    Text("Please keep the app open until the download finishes.")
                    uiState.updateDownloadProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } ?: CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) {
                    Text("Downloading…")
                }
            },
        )
    }

    if (!uiState.isLoading && uiState.profile == null) {
        return
    }

    Column(
        modifier = Modifier.rpsScreenPadding(),
    ) {
        if (uiState.isLoading && uiState.profile == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        val profile = uiState.profile
        Text(
            text = "Welcome, ${profile?.displayName ?: DisplayNames.DEFAULT}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        uiState.onlinePlayerCount?.let { count ->
            Spacer(modifier = Modifier.height(4.dp))
            PlayersOnlineLabel(count = count)
        }
        Spacer(modifier = Modifier.height(12.dp))

        HomeProfileSummaryCard(
            profile = profile,
            onClick = onProfile,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onFindMatch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Find Match")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLeaderboard,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Leaderboard")
        }

        Spacer(modifier = Modifier.weight(1f))

        HomeAppInfoFooter(
            versionName = uiState.versionName,
            updatesEnabled = BuildConfig.GITHUB_UPDATES_ENABLED,
            availableUpdate = uiState.availableUpdate,
            isCheckingForUpdate = uiState.isCheckingForUpdate,
            isDownloadingUpdate = uiState.isDownloadingUpdate,
            updateMessage = uiState.updateMessage,
            onCheckForUpdate = { viewModel.checkForUpdate(context) },
            onInstallUpdate = {
                activity?.let { viewModel.downloadAndInstallUpdate(it) }
                    ?: viewModel.showUpdatePrompt()
            },
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
private fun HomeProfileSummaryCard(
    profile: UserProfile?,
    onClick: () -> Unit,
) {
    val elo = profile?.elo ?: 1000
    val wins = profile?.wins ?: 0
    val losses = profile?.losses ?: 0
    val games = wins + losses
    val winRate = games.takeIf { it > 0 }?.let { (wins * 100) / it }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            val statStyle = MaterialTheme.typography.bodyMedium
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$elo",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "ELO",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = buildString {
                            append("W $wins · L $losses")
                            winRate?.let { append(" · $it%") }
                        },
                        style = statStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    profile?.throwsPerWin()?.let { throwsPerWin ->
                        RpsPerWinLabel(
                            throwsPerWin = throwsPerWin,
                            textStyle = statStyle,
                        )
                    }
                    ThrowCountRow(
                        rock = profile?.throwsRock ?: 0,
                        paper = profile?.throwsPaper ?: 0,
                        scissors = profile?.throwsScissors ?: 0,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = statStyle,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    )
                }
            }
        }
    }
}

