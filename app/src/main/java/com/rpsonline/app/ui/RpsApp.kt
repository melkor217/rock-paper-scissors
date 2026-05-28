package com.rpsonline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.data.repository.PresenceRepository
import com.rpsonline.app.data.monitoring.NetworkConnectionMonitor
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.SoundPreferences
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.R
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.components.AppearanceMenuButton
import com.rpsonline.app.ui.components.ClockSoundMuteButton
import com.rpsonline.app.ui.components.NetworkConnectionIndicator
import com.rpsonline.app.ui.util.applyImmersiveFullscreen
import com.rpsonline.app.ui.util.findActivity
import com.rpsonline.app.ui.util.formatQueueTime
import com.rpsonline.app.ui.theme.RpsTheme
import com.rpsonline.app.ui.util.ClockTickPlayer
import com.rpsonline.app.ui.util.RoundResolutionSoundEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RpsApp() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val themePreferences = remember { ThemePreferences(context) }
    val soundPreferences = remember { SoundPreferences(context) }
    var themeStyle by remember { mutableStateOf(themePreferences.get()) }
    var clockSoundMuted by remember { mutableStateOf(soundPreferences.isClockMuted()) }
    val scope = rememberCoroutineScope()
    val connectionMonitor = remember { NetworkConnectionMonitor(context) }
    val connectionStatus by connectionMonitor.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(connectionMonitor) {
        connectionMonitor.start(scope)
        onPauseOrDispose { connectionMonitor.stop() }
    }
    DisposableEffect(Unit) {
        onDispose { connectionMonitor.stop() }
    }

    LifecycleResumeEffect(activity) {
        activity?.applyImmersiveFullscreen()
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        MatchSessionMonitor.ensureStarted()
        onDispose { }
    }

    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val user by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)
    val activeMatch by MatchSessionMonitor.activeMatch.collectAsStateWithLifecycle()
    val queueJoinedAtMs by MatchSessionMonitor.queueJoinedAtMs.collectAsStateWithLifecycle()

    LaunchedEffect(queueJoinedAtMs) {
        if (queueJoinedAtMs == null) return@LaunchedEffect
        var consecutiveFailures = 0
        while (true) {
            if (!matchRepository.sendQueueHeartbeat()) {
                consecutiveFailures += 1
                if (consecutiveFailures >= 3) {
                    MatchSessionMonitor.clearQueueState()
                    break
                }
            } else {
                consecutiveFailures = 0
            }
            delay(PresenceRepository.HEARTBEAT_INTERVAL_MS)
        }
    }

    LifecycleResumeEffect(queueJoinedAtMs) {
        if (queueJoinedAtMs != null) {
            scope.launch {
                matchRepository.sendQueueHeartbeat()
            }
        }
        onPauseOrDispose { }
    }

    RpsTheme(style = themeStyle) {
        CompositionLocalProvider(LocalClockSoundMuted provides clockSoundMuted) {
            GlobalMatchClockTickEffect(
                activeMatch = activeMatch,
                userId = user?.uid,
                muted = clockSoundMuted,
            )
            RoundResolutionSoundEffect(
                activeMatch = activeMatch,
                userId = user?.uid,
            )
            val topPanelColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            val topPanelGradient = Brush.linearGradient(
                colors = listOf(
                    topPanelColor.copy(alpha = 0.98f),
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                    topPanelColor,
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                    topPanelColor,
                ),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                RpsNavGraph()
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    shape = RectangleShape,
                    color = topPanelColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(topPanelGradient)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        NetworkConnectionIndicator(status = connectionStatus)
                        Spacer(modifier = Modifier.width(8.dp))
                        QueueOrMatchStatusLabel(
                            activeMatch = activeMatch,
                            queueJoinedAtMs = queueJoinedAtMs,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 36.dp,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                ClockSoundMuteButton(
                                    muted = clockSoundMuted,
                                    onMutedChange = { muted ->
                                        clockSoundMuted = muted
                                        soundPreferences.setClockMuted(muted)
                                    },
                                )
                                AppearanceMenuButton(
                                    currentStyle = themeStyle,
                                    onStyleSelected = { style ->
                                        themeStyle = style
                                        themePreferences.set(style)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalMatchClockTickEffect(
    activeMatch: Match?,
    userId: String?,
    muted: Boolean,
) {
    val tickPlayer = remember { ClockTickPlayer() }

    val myClockRunning = remember(activeMatch, userId) {
        val match = activeMatch
        val uid = userId
        val openRound = match?.openRound()
        val hasSubmittedMove = when {
            uid == null || match == null || openRound == null -> false
            uid == match.player1 -> openRound.player1Choice != null
            else -> openRound.player2Choice != null
        }
        match?.status == MatchStatus.ACTIVE &&
            openRound != null &&
            !hasSubmittedMove
    }

    DisposableEffect(Unit) {
        onDispose { tickPlayer.release() }
    }

    LaunchedEffect(myClockRunning, muted) {
        if (!myClockRunning || muted) return@LaunchedEffect
        while (true) {
            tickPlayer.playTick()
            delay(500)
        }
    }
}

@Composable
private fun QueueOrMatchStatusLabel(
    activeMatch: Match?,
    queueJoinedAtMs: Long?,
) {
    var queueElapsedSeconds by remember(queueJoinedAtMs) { mutableStateOf(0L) }

    LaunchedEffect(queueJoinedAtMs) {
        val joinedAt = queueJoinedAtMs ?: return@LaunchedEffect
        while (true) {
            queueElapsedSeconds = ((System.currentTimeMillis() - joinedAt) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    val inMatch = activeMatch?.status == MatchStatus.ACTIVE
    val label = when {
        inMatch -> stringResource(R.string.in_match)
        queueJoinedAtMs != null -> stringResource(
            R.string.in_queue_with_time,
            formatQueueTime(queueElapsedSeconds),
        )
        else -> null
    } ?: return

    androidx.compose.material3.Surface(
        color = Color.Transparent,
        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
