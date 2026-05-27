package com.rpsonline.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rpsonline.app.data.repository.AuthRepository
import com.rpsonline.app.domain.MatchMode
import com.rpsonline.app.viewmodel.HomeViewModel
import com.rpsonline.app.viewmodel.MatchmakingViewModel
import com.rpsonline.app.ui.auth.SignInScreen
import com.rpsonline.app.ui.changelog.ChangelogScreen
import com.rpsonline.app.ui.game.GameScreen
import com.rpsonline.app.ui.home.HomeScreen
import com.rpsonline.app.ui.leaderboard.LeaderboardScreen
import com.rpsonline.app.ui.matchmaking.MatchmakingScreen
import com.rpsonline.app.ui.profile.ProfileScreen
import com.rpsonline.app.ui.result.ResultScreen

object Routes {
    const val SIGN_IN = "sign_in"
    const val HOME = "home?matchModes={matchModes}"

    fun home(autoMatchmake: Set<MatchMode>? = null): String =
        if (autoMatchmake != null) {
            "home?matchModes=${MatchMode.encodeRouteArg(autoMatchmake)}"
        } else {
            "home?matchModes="
        }
    const val MATCHMAKING = "matchmaking/{matchModes}"

    fun matchmaking(matchModes: Set<MatchMode>) =
        "matchmaking/${MatchMode.encodeRouteArg(matchModes)}"
    const val GAME = "game/{matchId}"
    const val RESULT = "result/{matchId}"
    const val LEADERBOARD = "leaderboard"
    const val CHANGELOG = "changelog"
    const val PROFILE = "profile/{userId}"

    fun game(matchId: String) = "game/$matchId"
    fun result(matchId: String) = "result/$matchId"
    fun profile(userId: String) = "profile/$userId"
}

@Composable
private fun MatchFoundNavigationEffect(navController: NavHostController) {
    val backStackEntries by navController.currentBackStack.collectAsStateWithLifecycle()
    val homeBackStackEntry = backStackEntries.lastOrNull { entry ->
        entry.destination.route?.startsWith("home") == true
    }
    val homeViewModel = homeBackStackEntry?.let { viewModel<HomeViewModel>(it) }
    val navigateToGameMatchId by homeViewModel?.navigateToGameMatchId?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navigateToGameMatchId, homeViewModel) {
        val matchId = navigateToGameMatchId ?: return@LaunchedEffect
        val viewModel = homeViewModel ?: return@LaunchedEffect
        navController.navigate(Routes.game(matchId)) {
            popUpTo(Routes.HOME)
        }
        viewModel.consumeNavigateToGameMatch()
    }
}

@Composable
fun RpsNavGraph(
    onRouteChanged: (String?) -> Unit = {},
) {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository() }
    var isSignedIn by remember { mutableStateOf(authRepository.currentUser != null) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        onRouteChanged(currentBackStackEntry?.destination?.route)
    }

    LaunchedEffect(Unit) {
        authRepository.authStateFlow().collect { user ->
            isSignedIn = user != null
            if (user == null) {
                navController.navigate(Routes.SIGN_IN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
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
                onSignedIn = {
                    isSignedIn = true
                    navController.navigate(Routes.home()) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
                onChangelog = { navController.navigate(Routes.CHANGELOG) },
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
            route = Routes.MATCHMAKING,
            arguments = listOf(navArgument("matchModes") { type = NavType.StringType }),
        ) { backStackEntry ->
            val matchModes = MatchMode.parseRouteArg(backStackEntry.arguments?.getString("matchModes"))
            MatchmakingScreen(
                matchModes = matchModes,
                viewModel = viewModel(factory = MatchmakingViewModel.factory(matchModes)),
                onMatchFound = { matchId ->
                    navController.navigate(Routes.game(matchId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
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
                onOpponentProfile = { userId ->
                    navController.navigate(Routes.profile(userId))
                },
            )
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                onPlayerProfile = { userId ->
                    navController.navigate(Routes.profile(userId))
                },
            )
        }

        composable(Routes.CHANGELOG) {
            ChangelogScreen()
        }

        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ProfileScreen(
                userId = userId,
            )
        }
    }

    MatchFoundNavigationEffect(navController)
}
