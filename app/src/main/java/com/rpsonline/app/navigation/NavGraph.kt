package com.rpsonline.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpsonline.app.viewmodel.HomeViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.data.repository.MatchRepository
import com.rpsonline.app.data.repository.MatchSessionMonitor
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.ui.auth.SignInScreen
import com.rpsonline.app.ui.changelog.ChangelogScreen
import com.rpsonline.app.ui.game.GameScreen
import com.rpsonline.app.ui.home.HomeScreen
import com.rpsonline.app.ui.leaderboard.LeaderboardScreen
import com.rpsonline.app.ui.profile.ProfileScreen
import com.rpsonline.app.ui.result.ResultScreen
import kotlinx.coroutines.delay

object Routes {
    const val SIGN_IN = "sign_in"
    const val HOME = "home?matchModes={matchModes}"

    fun home(autoMatchmake: Set<MatchMode>? = null): String =
        if (autoMatchmake != null) {
            "home?matchModes=${MatchMode.encodeRouteArg(autoMatchmake)}"
        } else {
            "home?matchModes="
        }
    const val GAME = "game/{matchId}"
    const val RESULT = "result/{matchId}"
    const val LEADERBOARD = "leaderboard"
    const val CHANGELOG = "changelog"
    const val PROFILE = "profile/{userId}"

    fun game(matchId: String) = "game/$matchId"
    fun result(matchId: String) = "result/$matchId"
    fun profile(userId: String) = "profile/$userId"
}

private fun NavHostController.navigateToHome() {
    navigate(Routes.home()) {
        popUpTo(Routes.HOME) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun MatchFoundNavigationEffect(navController: NavHostController) {
    val pendingMatchId by MatchSessionMonitor.pendingGameNavigationMatchId
        .collectAsStateWithLifecycle()
    val backStackEntries by navController.currentBackStack.collectAsStateWithLifecycle()
    val currentRoute = backStackEntries.lastOrNull()?.destination?.route

    LaunchedEffect(pendingMatchId, currentRoute) {
        val matchId = pendingMatchId ?: return@LaunchedEffect
        if (currentRoute?.startsWith("game/") == true) {
            MatchSessionMonitor.consumeGameNavigation()
            MatchSessionMonitor.setMatchmakingInProgress(false)
            return@LaunchedEffect
        }
        navController.navigate(Routes.game(matchId)) {
            popUpTo(Routes.HOME)
        }
        MatchSessionMonitor.consumeGameNavigation()
        MatchSessionMonitor.setMatchmakingInProgress(false)
    }
}

@Composable
fun RpsNavGraph() {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository() }
    var isSignedIn by remember { mutableStateOf(authRepository.currentUser != null) }

    LaunchedEffect(navController) {
        authRepository.authStateFlow().collect { user ->
            isSignedIn = user != null
            if (user == null) {
                val onSignIn = navController.currentBackStackEntry?.destination?.route == Routes.SIGN_IN
                if (!onSignIn) {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val startDestination = if (authRepository.currentUser != null) Routes.home() else Routes.SIGN_IN

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onChangelog = { navController.navigate(Routes.CHANGELOG) },
                onSignedIn = {
                    navController.navigate(Routes.home()) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.HOME,
            arguments = listOf(
                navArgument("matchModes") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val autoMatchmake = backStackEntry.arguments
                ?.getString("matchModes")
                ?.takeIf { it.isNotBlank() }
                ?.let { MatchMode.parseRouteArg(it) }
            val homeViewModel: HomeViewModel = viewModel(backStackEntry)
            if (isSignedIn) {
                HomeScreen(
                    autoStartMatchModes = autoMatchmake,
                    viewModel = homeViewModel,
                    onReconnectToGame = { matchId ->
                        navController.navigate(Routes.game(matchId))
                    },
                    onLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                    onProfile = {
                        authRepository.currentUserId?.let { uid ->
                            navController.navigate(Routes.profile(uid))
                        }
                    },
                    onChangelog = { navController.navigate(Routes.CHANGELOG) },
                )
            }
        }

        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
            GameScreen(
                matchId = matchId,
                onMatchComplete = { completedMatchId ->
                    navController.navigate(Routes.result(completedMatchId)) {
                        popUpTo(Routes.game(matchId)) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
            ResultScreen(
                matchId = matchId,
                onPlayAgain = { matchMode ->
                    navController.navigate(Routes.home(setOf(matchMode))) {
                        popUpTo(Routes.RESULT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onHome = { navController.navigateToHome() },
                onOpponentProfile = { userId ->
                    navController.navigate(Routes.profile(userId))
                },
            )
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                onHome = { navController.navigateToHome() },
                onPlayerProfile = { userId ->
                    navController.navigate(Routes.profile(userId))
                },
            )
        }

        composable(Routes.CHANGELOG) {
            ChangelogScreen(
                onHome = {
                    if (isSignedIn) {
                        navController.navigateToHome()
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ProfileScreen(
                userId = userId,
                onHome = { navController.navigateToHome() },
            )
        }
    }

    MatchFoundNavigationEffect(navController)
    NonGameMatchResolutionEffect(navController)
}

@Composable
private fun NonGameMatchResolutionEffect(navController: NavHostController) {
    val authRepository = remember { AuthRepository() }
    val matchRepository = remember { MatchRepository() }
    val activeMatch by MatchSessionMonitor.activeMatch.collectAsStateWithLifecycle()
    val backStackEntries by navController.currentBackStack.collectAsStateWithLifecycle()

    val currentRoute = backStackEntries.lastOrNull()?.destination?.route
    val onGameRoute = currentRoute?.startsWith("game/") == true || currentRoute == Routes.GAME

    LaunchedEffect(activeMatch, onGameRoute) {
        if (onGameRoute) return@LaunchedEffect
        val userId = authRepository.currentUserId ?: return@LaunchedEffect
        val match = activeMatch ?: return@LaunchedEffect
        if (match.status != MatchStatus.ACTIVE || !match.isParticipant(userId)) return@LaunchedEffect

        while (true) {
            val latest = activeMatch
            if (latest == null || latest.status != MatchStatus.ACTIVE) break

            val openRound = latest.openRound()
            val deadline = openRound?.deadline
            val roundNumber = openRound?.roundNumber
            if (deadline == null || roundNumber == null || System.currentTimeMillis() < deadline) {
                delay(1_000)
                continue
            }

            val myChoiceSubmitted = when (userId) {
                latest.player1 -> openRound.player1Choice != null
                latest.player2 -> openRound.player2Choice != null
                else -> true
            }
            if (myChoiceSubmitted) {
                delay(1_000)
                continue
            }

            runCatching {
                matchRepository.requestRoundTimeout(latest.id, roundNumber)
            }.onFailure { error ->
                if (!isIgnorableResolutionFailure(error)) {
                    // Keep retrying in background; this path is intentionally silent.
                }
            }
            delay(3_000)
        }
    }
}

private fun isIgnorableResolutionFailure(error: Throwable): Boolean {
    if (error is FirebaseFirestoreException &&
        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    ) {
        return true
    }
    val message = error.message.orEmpty()
    if (message.contains("PERMISSION_DENIED", ignoreCase = true)) return true
    if (message.contains("cancelled", ignoreCase = true)) return true
    return false
}
