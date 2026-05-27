# Android UI structure

Jetpack Compose UI for the Android client. Package root: `com.rpsonline.app.ui`.

For repository layout and backend flow, see [STRUCTURE.md](STRUCTURE.md).

## Layout

```
ui/
├── RpsApp.kt              # Theme + nav + global appearance control
├── auth/                  # Sign-in screen
├── home/                  # Home screen + inline queue + app info footer
├── game/                  # In-match screen + round UI
├── result/                # Post-match screen
├── leaderboard/           # Leaderboard screen + chart/podium widgets
├── profile/               # Player profile + match history
├── components/            # Shared composables
├── theme/                 # Material theme, colors, backgrounds
└── util/                  # Sound, autofill, activity helpers
```

Navigation is defined in `navigation/NavGraph.kt` (`RpsNavGraph`, `Routes`).

## App shell

| Piece | File | Role |
|-------|------|------|
| `RpsApp` | `ui/RpsApp.kt` | Wraps `RpsNavGraph` in `RpsTheme`; overlays `AppearanceMenuButton` (top-end) |
| `RpsNavGraph` | `navigation/NavGraph.kt` | `NavHost`, auth redirect, route wiring |
| `RpsTheme` | `ui/theme/Theme.kt` | Material 3 color scheme from `AppThemeStyle` |
| Screen padding | `ui/components/SafeScreen.kt` | `Modifier.rpsScreenPadding()` — safe drawing insets + top space for appearance button |

## Navigation

```mermaid
flowchart LR
  SignIn --> Home
  Home --> Game
  Home --> Leaderboard
  Home --> Profile
  Game --> Result
  Result --> Home
  Result --> Profile
  Leaderboard --> Profile
  Profile -->|home| Home
```

| Route | Constant | Arguments |
|-------|----------|-----------|
| Sign in | `sign_in` | — |
| Home | `home?matchModes={matchModes}` | optional `matchModes` (auto-queue from Play Again) |
| Game | `game/{matchId}` | `matchId` |
| Result | `result/{matchId}` | `matchId` |
| Leaderboard | `leaderboard` | — |
| Profile | `profile/{userId}` | `userId` |

Signed-out users are sent to sign-in and the back stack is cleared. Home queues for a match inline; match-found navigation opens the game. Result “play again” returns to home with auto-queue for the same format.

## Screens

Top-level `@Composable` screens registered in `NavGraph`. Unless noted, screens use `Modifier.rpsScreenPadding()`.

| Screen | Package / file | ViewModel | Purpose |
|--------|----------------|-----------|---------|
| **SignInScreen** | `ui/auth/SignInScreen.kt` | `SignInViewModel`, `AppUpdateViewModel` | Google, email (sign-in / register), guest; optional update check |
| **HomeScreen** | `ui/home/HomeScreen.kt` | `HomeViewModel`, `AppUpdateViewModel` | Welcome, profile summary, online count, inline matchmaking queue, Find Match / Leaderboard, sign out, app info footer |
| **GameScreen** | `ui/game/GameScreen.kt` | `GameViewModel` (per `matchId`) | Live match: pre-game countdown, score, round banners, move picker |
| **ResultScreen** | `ui/result/ResultScreen.kt` | *(inline repos)* | Final score, ELO change, opponent stats, round recap; play again / home |
| **LeaderboardScreen** | `ui/leaderboard/LeaderboardScreen.kt` | `LeaderboardViewModel` | Podium + ranked list; tap row → profile |
| **ProfileScreen** | `ui/profile/ProfileScreen.kt` | `ProfileViewModel` | Stats card + match history list (own last 10 or shared matches) |

### Game sub-flows (same route, inside `GameScreen`)

| UI | File | When shown |
|----|------|------------|
| **PreGameCountdownScreen** | `ui/game/PreGameCountdownScreen.kt` | Before first round; ELO matchup, skip |
| **RoundCountdown** | `ui/game/RoundCountdown.kt` | Active round deadline |
| **WinRoundBanner** / **LoseRoundBanner** / **DrawRoundBanner** | `ui/game/*RoundBanner.kt` | After choices revealed; built on **RoundOutcomeCard** (`RoundBannerCommon.kt`) |
| **WaitingForOpponentCard** | `ui/game/WaitingForOpponentCard.kt` | After local move submitted |
| **MovePicker** | `ui/components/MovePicker.kt` | Rock / paper / scissors selection |
| **MatchScoreCard** *(private)* | `GameScreen.kt` | You vs opponent win count |

`GameScreen` navigates out when match status is `COMPLETED` or `ABANDONED`.

## Screen-local widgets

Composable helpers colocated with a feature (not in `components/`).

### `ui/home/`

| Widget | File | Used by |
|--------|------|---------|
| `HomeAppInfoFooter` | `HomeAppInfoFooter.kt` | Home, Sign-in — version, manual update check/install |
| `HomeProfileSummaryCard` *(private)* | `HomeScreen.kt` | Home — wraps `ProfileSummaryStatsCard` with chevron → profile |

### `ui/leaderboard/`

| Widget | File | Role |
|--------|------|------|
| `LeaderboardPodium` | `LeaderboardPodium.kt` | Top-3 podium layout |
| `LeaderboardEntryCard` | `LeaderboardPodium.kt` | Single list row (rank, name, stats) |
| `ThrowDistributionRadialChart` | `ThrowDistributionRadialChart.kt` | R/P/S distribution ring |
| `RpsPerWinLabel` / `PlayerThrowStatsColumn` | `RpsPerWinLabel.kt` | Throws-per-win metric + throw breakdown |
| `LeaderboardSpectrumColor` | `LeaderboardSpectrumColor.kt` | Rank / RPS-per-win / ELO color scales |
| `leaderboardWinRateColor` | `LeaderboardWinRate.kt` | Win-rate accent color |

### `ui/profile/` *(private)*

| Widget | Role |
|--------|------|
| `ProfileStatsCard` | `ProfileSummaryStatsCard` without header click |
| `MatchHistoryCard` | `RpsCard` + `MatchHistoryCardHeader` + embedded `MatchRecapCard` |

## Shared components (`ui/components/`)

Reused across multiple screens.

### Layout & chrome

| Widget | File | Role |
|--------|------|------|
| `rpsScreenPadding` | `SafeScreen.kt` | Standard full-screen inset modifier |
| `RpsCard` | `RpsCard.kt` | Bordered surface card; optional `onClick` |
| `RpsLoadingColumn` | `RpsLoadingColumn.kt` | Centered spinner + optional message |
| `AppearanceMenuButton` | `AppearanceMenuButton.kt` | Theme style picker (global overlay) |

### Player & match stats

| Widget | File | Role |
|--------|------|------|
| `ProfileSummaryStatsCard` | `ProfileSummaryStatsCard.kt` | ELO, W–L, throw chips, optional header/chevron |
| `PlayerStatsWidget` | `PlayerStatsWidget.kt` | Thin wrapper: named header + `ProfileSummaryStatsCard` |
| `WinLossStatLine` | `WinLossStatLine.kt` | W / L / D / win-% stat line |
| `EloRatingText` | `EloRatingText.kt` | Styled ELO number |
| `ThrowCountRow` | `ThrowCountChips.kt` | Rock / paper / scissors counts |
| `PlayersOnlineLabel` | `PlayersOnlineLabel.kt` | Presence count copy |

### Match display

| Widget | File | Role |
|--------|------|------|
| `MatchRecapCard` | `MatchRecap.kt` | Per-round recap list (standalone card or embedded) |
| `RoundRecapRow` | `MatchRecap.kt` | One round: moves + outcome colors |
| `MatchHistoryCardHeader` | `MatchHistoryCardHeader.kt` | Profile history row: date, opponent, score, ELO delta |
| `MatchEloChangeLabel` | `MatchEloChangeLabel.kt` | Post-match ELO change line |
| `MatchFormat` *(functions)* | `MatchFormat.kt` | `formatMatchScore`, `formatEloDelta`, `formatMatchResultLine`, `postMatchElo` |

### Gameplay & auth

| Widget | File | Role |
|--------|------|------|
| `MovePicker` | `MovePicker.kt` | Three move buttons |
| `AppUpdateDialogs` | `AppUpdateDialogs.kt` | Optional / required update prompts |
| `AutofillTextField` | `AutofillTextField.kt` | Sign-in fields with autofill hints |
| Autofill modifiers | `AutofillModifiers.kt` | `autofillEmailAddress`, `autofillPassword`, etc. |

## Theme (`ui/theme/`)

| File | Role |
|------|------|
| `Theme.kt` | `RpsTheme(style)` — applies `colorSchemeFor` + `appBackground` |
| `Color.kt` / `ColorSchemes.kt` | Palette and per-style schemes |
| `AppBackground.kt` | Gradient / cyberpunk backgrounds per theme |
| `RpsThemeLocals.kt` | `isRpsDarkTheme()`, `currentAppThemeStyle()` |

Theme preference is persisted via `data/preferences/ThemePreferences.kt` (`AppThemeStyle`).

## Utilities (`ui/util/`)

| Helper | Role |
|--------|------|
| `playMatchFoundSound` | `MatchFoundSound.kt` — queue → game transition |
| `commitAutofillSave` | `AutofillCommit.kt` — after successful sign-in |
| `findActivity` | `ActivityContext.kt` — for update install intent |
| `NetworkUtils` | Connectivity messaging on sign-in |

## Composition cheat sheet

Which shared widgets each screen uses:

| Screen | Components |
|--------|------------|
| Sign-in | `RpsLoadingColumn`, `AppUpdateDialogs`, `AutofillTextField`, `HomeAppInfoFooter` |
| Home | `ProfileSummaryStatsCard`, `PlayersOnlineLabel`, `RpsLoadingColumn`, `AppUpdateDialogs` |
| Matchmaking | `PlayersOnlineLabel`, `rpsScreenPadding` |
| Game | `MovePicker`, `RpsLoadingColumn` + game-local banners/countdown |
| Result | `RpsLoadingColumn`, `RpsCard`, `MatchRecapCard`, `MatchEloChangeLabel`, `PlayerStatsWidget` |
| Leaderboard | `RpsLoadingColumn`, `EloRatingText`, `WinLossStatLine` + leaderboard widgets |
| Profile | `ProfileSummaryStatsCard`, `MatchHistoryCardHeader`, `MatchRecapCard`, `RpsCard`, `RpsLoadingColumn` |

## Adding UI

1. **New top-level screen** — Add route in `Routes`, `composable { }` in `NavGraph`, screen file under `ui/<feature>/`, and a `ViewModel` if state is non-trivial.
2. **Reusable piece** — Prefer `ui/components/` if two or more screens need it; otherwise keep it next to the screen (see `ui/game/`).
3. **Padding** — Use `Modifier.rpsScreenPadding()` on the root column so content clears the appearance button and system bars.
