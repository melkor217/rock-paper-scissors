# Project structure

High-level map of the repository. For setup and builds, see [README.md](../README.md).

## Repository

```
rock-paper-scissors/
├── app/                 # Android client (Kotlin, Jetpack Compose)
├── functions/           # Firebase Cloud Functions (TypeScript)
├── firestore.rules      # Firestore security rules
├── docs/                # Docs and GitHub Pages assets (e.g. asset links)
├── scripts/             # Deploy and environment helpers
├── .github/workflows/   # CI: APK on PR, release on version tags
├── gradle/              # Gradle wrapper
├── LICENSE
└── README.md
```

## Android app (`app/`)

Package root: `com.rpsonline.app`.

| Layer | Path | Role |
|-------|------|------|
| Entry | `MainActivity.kt`, `RpsApplication.kt` | Activity, Firebase init |
| Navigation | `navigation/NavGraph.kt` | Routes and screen flow |
| UI | `ui/` | Compose screens and components — see [UI.md](UI.md) |
| ViewModel | `viewmodel/` | Screen state, Firestore listeners |
| Data | `data/repository/`, `data/model/` | Firebase access, DTOs |
| Domain | `domain/` | `MatchMode`, `GameRules`, ELO display, weekly chart, match result copy |
| Preferences | `data/preferences/` | Theme style, match-mode selection, clock sound mute |
| Monitoring | `data/monitoring/` | Firestore RTT probe via callable `ping` |
| Update | `data/update/` | GitHub release check, APK install, changelog fetch |

### Screens (`ui/`)

Full screen list, navigation graph, and shared widgets: **[docs/UI.md](UI.md)**.

| Screen | Package | Purpose |
|--------|---------|---------|
| Sign in | `ui/auth/` | Google, email, guest |
| Home | `ui/home/` | Play, inline queue, profile summary, app info |
| Game | `ui/game/` | Round play, timer, score, moves |
| Result | `ui/result/` | Final score, ELO, match recap |
| Leaderboard | `ui/leaderboard/` | Top players, podium |
| Profile | `ui/profile/` | Stats, weekly ELO chart, paginated match history |
| Changelog | `ui/changelog/` | Release notes from GitHub (version footer) |

Flow: **Sign in → Home → Game → Result** (queue and match-found happen on Home). **Leaderboard**, **Profile**, and **Changelog** are reachable from Home, leaderboard rows, result opponent link, or the version footer.

`RpsApp` wraps the nav graph with global overlays: Firebase ping meter, queue/match status chip, theme picker, and clock-sound mute. `MatchSessionMonitor` keeps the active match in sync for reconnect and background round sounds.

ViewModels load data via repositories (`AuthRepository`, `UserRepository`, `MatchRepository`, `PresenceRepository`, `AppUpdateRepository`) and expose `StateFlow` / UI state to Composables.

## Backend (`functions/`)

| File | Role |
|------|------|
| `src/index.ts` | Firestore triggers, schedulers, match lifecycle |
| `src/game.ts` | Move validation, round/series resolution, ELO math, match resolution |
| `src/clockControl.ts` | Per-player match clocks (90s start, +5s per round) |
| `src/moveTiming.ts` | Round deadline (60s), per-move timing fields |
| `src/*.test.ts` | Unit tests for game, clock, and move timing |

### Cloud Functions

| Export | Trigger | Purpose |
|--------|---------|---------|
| `onQueueEntry` | `queue/{userId}` created | ELO-based pairing, create `matches` doc |
| `onPlayerChoice` | `matches/{id}/rounds/{n}/choices/{uid}` created | Apply move, resolve round, advance match |
| `onRoundTimeout` | `matches/{id}/rounds/{n}/timeoutRequests/{id}` created | Resolve round when deadline or clock expires |
| `resolveTimedOutRounds` | Scheduled (every 1 min) | Backstop for missed round deadlines |
| `cleanupStale` | Scheduled (every 5 min) | Stale queue entries and abandoned matches |
| `ping` | HTTPS Callable | Auth-gated RTT probe for in-app connection meter |

Matchmaking and moves use **Firestore writes only**; functions update `matches` (clients read the match doc, not write it). The only callable used by the app is `ping`.

## Firestore data model

| Collection / path | Written by | Read by |
|-------------------|------------|---------|
| `users/{uid}` | Client (profile create, `lastSeen` heartbeat); server (ELO, wins, losses, draws, move timing totals, throw counts, `activeMatchId`, `lastSeen`) | Client, functions |
| `queue/{uid}` | Client (join/leave matchmaking) | Client (own doc), functions |
| `matches/{id}` | Functions only | Both players |
| `matches/{id}/rounds/{n}/choices/{uid}` | Client (move) | Functions merge into match |
| `matches/{id}/rounds/{n}/timeoutRequests/{id}` | Client (timeout signal) | Functions |

Match document holds embedded `rounds[]` (choices, winner, deadlines, per-player `player1MoveMs` / `player2MoveMs`) plus match-level `player1MoveTimeMs` / `player2MoveTimeMs` (and move counts). Subcollections are the write path; the match doc is the source of truth for the UI listener. Each submitted move also increments `users/{uid}.moveTimeMs` and `moveCount`.

Security: [firestore.rules](../firestore.rules) — users cannot write `matches` directly or change their own ELO.

One-off backfills (require ADC; use `--dry-run` first where supported):

| Script | Purpose |
|--------|---------|
| `./scripts/backfill-user-last-seen.sh` | `users.lastSeen` |
| `./scripts/backfill-move-timing.sh` | Per-move timing on users/matches |
| `./scripts/backfill-throw-stats.sh` | Rock/paper/scissors throw counts |
| `./scripts/backfill-match-resolution.sh` | Match `resolution` field |

Additional backfills live under `functions/package.json` (`backfill-round-stats`, `backfill-round-end-reason`, etc.).

## Rules kept in sync

These values must match between client and server:

| Constant | App (`GameRules`) | Server (`functions/src/index.ts` / `game.ts`) |
|----------|-------------------|-----------------------------------------------|
| Wins to finish | `MatchMode.winsToFinish` (BO3=2, BO5=3, BO10=6) | `winsToFinish(mode)` in `game.ts` |
| Series draw | BO10 only: 5–5 after 10 rounds (`tiedSeriesScore`) | `seriesOutcomeAfterRound()` in `game.ts` |
| Match format | `matchModes` on queue + match docs | Same; queue pairs overlapping `matchModes` (random among shared BO3/BO5/BO10) |
| Round timeout | `ROUND_TIMEOUT_SECONDS = 60` | `ROUND_TIMEOUT_MS = 60_000` |
| Match clocks | `INITIAL_CLOCK_MS = 90_000`, `CLOCK_INCREMENT_MS = 5_000` | `clockControl.ts` (`player1ClockMs`, `player2ClockMs`, `clocksUpdatedAt`) |
| Move resolution | `resolveRound()` | `resolveRound()` in `game.ts` |

After changing rules, update both sides and redeploy functions.

**Deploy order:** when adding or changing match formats, deploy Cloud Functions and `firestore.rules` before (or with) an app release that writes `matchModes` on `queue/{uid}`. Until functions are updated, new clients still queue but pairing falls back to BO3-only behavior on the server.

## Related docs

- [scripts/ENABLE_AUTH.md](../scripts/ENABLE_AUTH.md) — Firebase Auth providers and SHA-1
- [scripts/FIX_UNAUTHENTICATED.md](../scripts/FIX_UNAUTHENTICATED.md) — Deploy / IAM issues
