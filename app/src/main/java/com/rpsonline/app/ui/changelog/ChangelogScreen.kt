package com.rpsonline.app.ui.changelog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.R
import com.rpsonline.app.data.update.ReleaseChangelog
import com.rpsonline.app.data.update.ReleaseChangelogEntry
import com.rpsonline.app.ui.components.HomeOutlinedButton
import com.rpsonline.app.ui.components.RpsLoadingColumn
import com.rpsonline.app.ui.components.rpsScreenPadding
import com.rpsonline.app.viewmodel.ChangelogViewModel

@Composable
fun ChangelogScreen(
    onHome: () -> Unit,
    viewModel: ChangelogViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.load(context)
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.layoutInfo.totalItemsCount,
        uiState.hasMore,
        uiState.isLoading,
        uiState.isLoadingMore,
    ) {
        if (!uiState.hasMore || uiState.isLoading || uiState.isLoadingMore) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return@LaunchedEffect
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisibleIndex >= layoutInfo.totalItemsCount - 2) {
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.rpsScreenPadding()) {
        Text(
            text = "Changelog",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Installed: v${uiState.versionName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading && uiState.entries.isEmpty() -> {
                RpsLoadingColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    message = stringResource(R.string.loading_release_notes),
                )
            }
            uiState.error != null && uiState.entries.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = uiState.entries,
                        key = { it.tag },
                    ) { entry ->
                        ChangelogEntrySection(
                            entry = entry,
                            isInstalledVersion = entry.versionLabel == uiState.versionName,
                        )
                    }
                    if (uiState.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HomeOutlinedButton(onClick = onHome)
    }
}

@Composable
private fun ChangelogEntrySection(
    entry: ReleaseChangelogEntry,
    isInstalledVersion: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = buildString {
                append("v")
                append(entry.versionLabel)
                if (isInstalledVersion) {
                    append(" (installed)")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ChangelogNotesList(notes = entry.notes)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ChangelogNotesList(notes: String) {
    val items = ReleaseChangelog.notesToListItems(notes)
    if (items.isEmpty()) {
        Text(
            text = ReleaseChangelog.NO_RELEASE_NOTES,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
