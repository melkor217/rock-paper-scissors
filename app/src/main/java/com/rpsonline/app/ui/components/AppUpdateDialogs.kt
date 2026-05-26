package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.rpsonline.app.viewmodel.AppUpdateUiState
import com.rpsonline.app.viewmodel.AppUpdateViewModel

@Composable
fun AppUpdateDialogs(
    updateState: AppUpdateUiState,
    activity: android.app.Activity?,
    viewModel: AppUpdateViewModel,
) {
    val pendingUpdate = updateState.availableUpdate?.takeIf {
        it.tag != updateState.dismissedUpdateTag
    }
    if (pendingUpdate != null && activity != null && !updateState.isDownloadingUpdate) {
        val scrollState = rememberScrollState()
        val maxChangelogHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${pendingUpdate.versionLabel} is available on GitHub.")
                    pendingUpdate.releaseNotes?.let { notes ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "What's new",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .heightIn(max = maxChangelogHeight)
                                .verticalScroll(scrollState),
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

    if (updateState.isDownloadingUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Downloading update",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Please keep the app open until the download finishes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    updateState.updateDownloadProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) {
                    Text(
                        text = "Downloading…",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}
