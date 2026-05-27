package com.rpsonline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
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
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.monitoring.FirebaseConnectionMonitor
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.SoundPreferences
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.components.AppearanceMenuButton
import com.rpsonline.app.ui.components.ClockSoundMuteButton
import com.rpsonline.app.ui.components.FirebasePingMeter
import com.rpsonline.app.ui.matchmaking.formatQueueTime
import com.rpsonline.app.ui.theme.RpsTheme
import com.rpsonline.app.ui.util.ClockTickPlayer
import com.rpsonline.app.ui.util.MoveSoundPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RpsApp() {
    val context = LocalContext.current
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

    RpsTheme(style = themeStyle) {
        CompositionLocalProvider(LocalClockSoundMuted provides clockSoundMuted) {
            GlobalMatchClockTickEffect(muted = clockSoundMuted)
            GlobalRoundResolutionBackgroundSoundEffect(
                muted = clockSoundMuted,
                currentRoute = currentRoute,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                RpsNavGraph(onRouteChanged = { route -> currentRoute = route })
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FirebasePingMeter(status = connectionStatus)
                    if (currentRoute?.startsWith("home") != true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        QueueOrMatchStatusChip()
                    }
                    Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun GlobalRoundResolutionBackgroundSoundEffect(
    muted: Boolean,
    currentRoute: String?,
) {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val context = LocalContext.current
    val moveSoundPlayer = remember(context) { MoveSoundPlayer(context) }
    val user by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    var baselineInitialized by remember { mutableStateOf(false) }
    var lastPlayedRoundKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { moveSoundPlayer.release() }
    }

    LaunchedEffect(user?.uid, muted, currentRoute) {
        baselineInitialized = false
        lastPlayedRoundKey = null
        if (user?.uid == null) return@LaunchedEffect

        matchRepository.observeActiveMatch().collect { match ->
            val uid = user?.uid ?: return@collect
            val resolved = match?.lastResolvedRound() ?: run {
                if (!baselineInitialized) baselineInitialized = true
                return@collect
            }
            if (resolved.player1Choice == null || resolved.player2Choice == null) {
                if (!baselineInitialized) baselineInitialized = true
                return@collect
            }

            val key = "${match.id}:${resolved.roundNumber}:${resolved.resolvedAt}"
            if (!baselineInitialized) {
                baselineInitialized = true
                lastPlayedRoundKey = key
                return@collect
            }
            if (key == lastPlayedRoundKey) return@collect

            lastPlayedRoundKey = key
            val onGameScreen = currentRoute?.startsWith("game/") == true
            if (muted || onGameScreen) return@collect

            val myChoice = if (uid == match.player1) resolved.player1Choice else resolved.player2Choice
            val move = Move.fromString(myChoice) ?: return@collect
            val repetitions = when (resolved.winner) {
                "tie" -> 2
                uid -> 3
                else -> 1
            }
            moveSoundPlayer.play(move, repetitions)
        }
    }
}

@Composable
private fun GlobalMatchClockTickEffect(muted: Boolean) {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val tickPlayer = remember { ClockTickPlayer() }

    val user by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)
    var myClockRunning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { tickPlayer.release() }
    }

    LaunchedEffect(user?.uid) {
        myClockRunning = false
        if (user?.uid == null) return@LaunchedEffect
        matchRepository.observeActiveMatch().collect { match ->
            val uid = user?.uid
            val openRound = match?.openRound()
            val hasSubmittedMove = when {
                uid == null || match == null || openRound == null -> false
                uid == match.player1 -> openRound.player1Choice != null
                else -> openRound.player2Choice != null
            }
            myClockRunning = match?.status == MatchStatus.ACTIVE &&
                openRound != null &&
                !hasSubmittedMove
        }
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
private fun QueueOrMatchStatusChip() {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val user by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    var inMatch by remember { mutableStateOf(false) }
    var queueJoinedAtMs by remember { mutableStateOf<Long?>(null) }
    var queueElapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(user?.uid) {
        inMatch = false
        queueJoinedAtMs = null
        queueElapsedSeconds = 0L
        if (user?.uid == null) return@LaunchedEffect

        launch {
            matchRepository.observeActiveMatch().collect { match ->
                inMatch = match?.status == MatchStatus.ACTIVE
            }
        }
        launch {
            matchRepository.observeQueue().collect { joinedAt ->
                queueJoinedAtMs = joinedAt
                if (joinedAt == null) {
                    queueElapsedSeconds = 0L
                }
            }
        }
    }

    LaunchedEffect(queueJoinedAtMs) {
        val joinedAt = queueJoinedAtMs ?: return@LaunchedEffect
        while (true) {
            queueElapsedSeconds = ((System.currentTimeMillis() - joinedAt) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }

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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
