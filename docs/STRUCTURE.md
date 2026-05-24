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
| UI | `ui/` | Compose screens and components |
| ViewModel | `viewmodel/` | Screen state, Firestore listeners |
| Data | `data/repository/`, `data/model/` | Firebase access, DTOs |
| Domain | `domain/GameRules.kt` | Client-side rules (must match server) |
| Update | `data/update/` | GitHub release check / APK install |

### Screens (`ui/`)

| Screen | Package | Purpose |
|--------|---------|---------|
| Sign in | `ui/auth/` | Google, email, guest |
| Home | `ui/home/` | Play, leaderboard preview, app info |
| Matchmaking | `ui/matchmaking/` | Queue until paired |
| Game | `ui/game/` | Round play, timer, score, moves |
| Result | `ui/result/` | Final score, ELO, match recap |
| Leaderboard | `ui/leaderboard/` | Top players, podium |

Flow: **Sign in → Home → Matchmaking → Game → Result** (or Home if match abandoned). Leaderboard is reachable from Home.

ViewModels load data via repositories (`AuthRepository`, `UserRepository`, `MatchRepository`, `AppUpdateRepository`) and expose `StateFlow` / UI state to Composables.

## Backend (`functions/`)

| File | Role |
|------|------|
| `src/index.ts` | Firestore triggers, schedulers, match lifecycle |
| `src/game.ts` | Move validation, round resolution, ELO math |
| `src/game.test.ts` | Unit tests for game logic |

### Cloud Functions (triggers)

| Export | Trigger | Purpose |
|--------|---------|---------|
| `onQueueEntry` | `queue/{userId}` created | ELO-based pairing, create `matches` doc |
| `onPlayerChoice` | `matches/.../choices/{userId}` created | Apply move, resolve round, advance match |
| `onRoundTimeout` | `matches/.../timeoutRequests/{id}` created | Resolve round when time runs out |
| `resolveTimedOutRounds` | Scheduled | Backstop for missed timeouts |
| `cleanupStale` | Scheduled | Stale queue entries and abandoned matches |

The client does **not** call HTTPS Callable functions for matchmaking or moves. It writes Firestore documents; functions react and update `matches` (clients read only).

## Firestore data model

| Collection / path | Written by | Read by |
|-------------------|------------|---------|
| `users/{uid}` | Client (profile create, `lastSeen` heartbeat); server (ELO, wins, losses, throw counts, `activeMatchId`, `lastSeen`) | Client, functions |
| `queue/{uid}` | Client (join/leave matchmaking) | Client (own doc), functions |
| `matches/{id}` | Functions only | Both players |
| `matches/{id}/rounds/{n}/choices/{uid}` | Client (move) | Functions merge into match |
| `matches/{id}/rounds/{n}/timeoutRequests/{id}` | Client (timeout signal) | Functions |

Match document holds embedded `rounds[]` (choices, winner, deadlines). Subcollections are the write path; the match doc is the source of truth for the UI listener.

Security: [firestore.rules](../firestore.rules) — users cannot write `matches` directly or change their own ELO.

One-off migration for `users.lastSeen`: `./scripts/backfill-user-last-seen.sh` (requires ADC; use `--dry-run` first).

## Rules kept in sync

These values must match between client and server:

| Constant | App (`GameRules`) | Server (`functions/src/index.ts` / `game.ts`) |
|----------|-------------------|-----------------------------------------------|
| Wins to finish | `WINS_TO_FINISH = 2` | `WINS_TO_FINISH = 2` |
| Round timeout | `ROUND_TIMEOUT_SECONDS = 60` | `ROUND_TIMEOUT_MS = 60_000` |
| Move resolution | `resolveRound()` | `resolveRound()` in `game.ts` |

After changing rules, update both sides and redeploy functions.

## Related docs

- [scripts/ENABLE_AUTH.md](../scripts/ENABLE_AUTH.md) — Firebase Auth providers and SHA-1
- [scripts/FIX_UNAUTHENTICATED.md](../scripts/FIX_UNAUTHENTICATED.md) — Deploy / IAM issues
