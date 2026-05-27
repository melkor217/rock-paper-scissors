package com.rpsonline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.data.monitoring.FirebaseConnectionMonitor
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.SoundPreferences
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.components.AppearanceMenuButton
import com.rpsonline.app.ui.components.ClockSoundMuteButton
import com.rpsonline.app.ui.components.FirebasePingMeter
import com.rpsonline.app.ui.util.applyImmersiveFullscreen
import com.rpsonline.app.ui.util.findActivity
import com.rpsonline.app.ui.util.formatQueueTime
import com.rpsonline.app.ui.theme.RpsTheme
import com.rpsonline.app.ui.util.ClockTickPlayer
import com.rpsonline.app.ui.util.MoveSoundPlayer
import kotlinx.coroutines.delay

@Composable
fun RpsApp() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val themePreferences = remember { ThemePreferences(context) }
    val soundPreferences = remember { SoundPreferences(context) }
    var themeStyle by remember { mutableStateOf(themePreferences.get()) }
    var clockSoundMuted by remember { mutableStateOf(soundPreferences.isClockMuted()) }
    var currentRoute by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val connectionMonitor = remember { FirebaseConnectionMonitor(context) }
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
    val user by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)
    val activeMatch by MatchSessionMonitor.activeMatch.collectAsStateWithLifecycle()
    val queueJoinedAtMs by MatchSessionMonitor.queueJoinedAtMs.collectAsStateWithLifecycle()

    RpsTheme(style = themeStyle) {
        CompositionLocalProvider(LocalClockSoundMuted provides clockSoundMuted) {
            GlobalMatchClockTickEffect(
                activeMatch = activeMatch,
                userId = user?.uid,
                muted = clockSoundMuted,
            )
            GlobalRoundResolutionBackgroundSoundEffect(
                activeMatch = activeMatch,
                userId = user?.uid,
                muted = clockSoundMuted,
                currentRoute = currentRoute,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                RpsNavGraph(onRouteChanged = { route -> currentRoute = route })
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                        )
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FirebasePingMeter(status = connectionStatus)
                    Spacer(modifier = Modifier.width(8.dp))
                    QueueOrMatchStatusChip(
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

@Composable
private fun GlobalRoundResolutionBackgroundSoundEffect(
    activeMatch: Match?,
    userId: String?,
    muted: Boolean,
    currentRoute: String?,
) {
    val context = LocalContext.current
    val moveSoundPlayer = remember(context) { MoveSoundPlayer(context) }

    var baselineInitialized by remember(userId) { mutableStateOf(false) }
    var lastPlayedRoundKey by remember(userId) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { moveSoundPlayer.release() }
    }

    LaunchedEffect(userId) {
        baselineInitialized = false
        lastPlayedRoundKey = null
    }

    LaunchedEffect(activeMatch, userId, muted, currentRoute) {
        if (userId == null) return@LaunchedEffect

        val match = activeMatch
        val resolved = match?.lastResolvedRound() ?: run {
            if (!baselineInitialized) baselineInitialized = true
            return@LaunchedEffect
        }
        if (resolved.player1Choice == null || resolved.player2Choice == null) {
            if (!baselineInitialized) baselineInitialized = true
            return@LaunchedEffect
        }

        val key = "${match.id}:${resolved.roundNumber}:${resolved.resolvedAt}"
        if (!baselineInitialized) {
            baselineInitialized = true
            lastPlayedRoundKey = key
            return@LaunchedEffect
        }
        if (key == lastPlayedRoundKey) return@LaunchedEffect

        lastPlayedRoundKey = key
        val onGameScreen = currentRoute?.startsWith("game/") == true
        if (muted || onGameScreen) return@LaunchedEffect

        val myChoice = if (userId == match.player1) resolved.player1Choice else resolved.player2Choice
        val move = Move.fromString(myChoice) ?: return@LaunchedEffect
        val repetitions = when (resolved.winner) {
            "tie" -> 2
            userId -> 3
            else -> 1
        }
        moveSoundPlayer.play(move, repetitions)
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
private fun QueueOrMatchStatusChip(
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
        inMatch -> "In match"
        queueJoinedAtMs != null -> "In queue: ${formatQueueTime(queueElapsedSeconds)}"
        else -> null
    } ?: return

    androidx.compose.material3.Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        shape = androidx.compose.material3.MaterialTheme.shapes.small,
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
