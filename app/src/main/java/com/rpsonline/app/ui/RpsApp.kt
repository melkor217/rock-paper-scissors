package com.rpsonline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.data.repository.PresenceRepository
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.SoundPreferences
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.components.AppearanceMenuButton
import com.rpsonline.app.ui.components.ClockSoundMuteButton
import com.rpsonline.app.ui.components.PlayersOnlineIndicator
import com.rpsonline.app.ui.components.SevenSegmentBlankSlot
import com.rpsonline.app.ui.components.TopBarSegmentedQueueIndicator
import com.rpsonline.app.ui.components.RpsTopStatusBar
import com.rpsonline.app.ui.util.applyImmersiveFullscreen
import com.rpsonline.app.ui.util.findActivity
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

    val presenceRepository = remember { PresenceRepository() }
    var onlinePlayerCount by remember { mutableStateOf<Int?>(null) }
    var onlineCountRefreshGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(user?.uid, onlineCountRefreshGeneration) {
        val uid = user?.uid
        if (uid == null) {
            onlinePlayerCount = null
            return@LaunchedEffect
        }
        presenceRepository.observeOnlineCount(selfUid = uid).collect { count ->
            onlinePlayerCount = count
        }
    }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        presenceRepository.touchPresence(uid, forceAuthRefresh = true, awaitServerAck = true)
        var heartbeat = 0
        while (true) {
            delay(PresenceRepository.HEARTBEAT_INTERVAL_MS)
            heartbeat++
            presenceRepository.touchPresence(
                uid,
                awaitServerAck = heartbeat % 2 == 0,
            )
        }
    }

    LifecycleResumeEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            onlineCountRefreshGeneration++
            scope.launch {
                runCatching { MatchSessionMonitor.refreshOnResume() }
                runCatching {
                    presenceRepository.touchPresence(uid, forceAuthRefresh = true, awaitServerAck = true)
                }
                runCatching { presenceRepository.fetchOnlineCountFromServer(selfUid = uid) }
                    .onSuccess { count -> onlinePlayerCount = count }
            }
        }
        onPauseOrDispose { }
    }
    val activeMatch by MatchSessionMonitor.activeMatch.collectAsStateWithLifecycle()
    val hasQueueEntry by MatchSessionMonitor.hasQueueEntry.collectAsStateWithLifecycle()
    val queueJoinedAtMs by MatchSessionMonitor.queueJoinedAtMs.collectAsStateWithLifecycle()
    var queueElapsedSeconds by remember(queueJoinedAtMs) { mutableStateOf(0L) }
    var matchElapsedSeconds by remember(activeMatch?.id) { mutableStateOf(0L) }

    LaunchedEffect(queueJoinedAtMs) {
        val joinedAt = queueJoinedAtMs
        if (joinedAt == null) {
            queueElapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            queueElapsedSeconds = ((System.currentTimeMillis() - joinedAt) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    LaunchedEffect(activeMatch?.id, activeMatch?.status, activeMatch?.createdAt) {
        val match = activeMatch
        if (match == null || match.status != MatchStatus.ACTIVE || match.createdAt <= 0L) {
            matchElapsedSeconds = 0L
            return@LaunchedEffect
        }
        val startedAtMs = match.createdAt
        while (true) {
            matchElapsedSeconds = ((System.currentTimeMillis() - startedAtMs) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    LaunchedEffect(hasQueueEntry) {
        if (!hasQueueEntry) return@LaunchedEffect
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

    LifecycleResumeEffect(hasQueueEntry) {
        if (hasQueueEntry) {
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
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    color = topPanelColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                ) {
                    RpsTopStatusBar(
                        background = Modifier.background(topPanelGradient),
                        leftContent = {
                            if (user != null) {
                                val inMatch = activeMatch?.status == MatchStatus.ACTIVE
                                val inQueue = queueJoinedAtMs != null && !inMatch
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    PlayersOnlineIndicator(count = onlinePlayerCount)
                                    SevenSegmentBlankSlot()
                                    TopBarSegmentedQueueIndicator(
                                        inMatch = inMatch,
                                        inQueue = inQueue,
                                        elapsedSeconds = if (inMatch) {
                                            matchElapsedSeconds
                                        } else {
                                            queueElapsedSeconds
                                        },
                                    )
                                }
                            }
                        },
                        rightContent = {
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides 28.dp,
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
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    RpsNavGraph()
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
