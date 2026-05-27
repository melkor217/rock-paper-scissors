# Android UI

Jetpack Compose under `com.rpsonline.app.ui`. Architecture, Firestore flow, and shared game rules: [STRUCTURE.md](STRUCTURE.md).

## Layout

```
ui/
├── RpsApp.kt           # Theme, nav, global overlays (ping, queue chip, sound mute, appearance)
├── auth/               # Sign-in
├── home/               # Queue, profile summary, match-mode pickers
├── game/               # Live match (clocks, banners, move picker)
├── result/             # Post-match recap
├── leaderboard/        # Rankings + throw charts
├── profile/            # Stats, weekly ELO chart, match history
├── changelog/          # Release notes
├── components/         # Shared composables (cards, stats, autofill, updates)
├── theme/              # Material 3 styles and backgrounds
└── util/               # Sound, queue time, immersive mode, autofill
```

Navigation: `navigation/NavGraph.kt` (`Routes`, `RpsNavGraph`).

## Routes

| Route | Purpose |
|-------|---------|
| `sign_in` | Google, email, guest |
| `home?matchModes=` | Play, queue, reconnect; optional auto-queue after “Play again” |
| `game/{matchId}` | Active match |
| `result/{matchId}` | Final score and recap |
| `leaderboard` | Rankings |
| `profile/{userId}` | Any player’s stats and history |
| `changelog` | GitHub release notes (version footer) |

Signed-out users are redirected to sign-in. Match-found navigation is driven from `HomeViewModel` when `MatchSessionMonitor` reports an active match.

## Conventions

1. **New screen** — Add a `Routes` entry, a `composable { }` in `NavGraph`, a file under `ui/<feature>/`, and a `ViewModel` when state is non-trivial.
2. **Shared UI** — Put reusable composables in `ui/components/`; keep screen-specific pieces next to the screen (see `ui/game/`).
3. **Insets** — Root columns use `Modifier.rpsScreenPadding()` so content clears the global top overlay (ping meter, queue chip, sound/appearance controls).
4. **Session state** — Queue and active-match listeners live in `MatchSessionMonitor`; screens collect those flows instead of opening parallel Firestore listeners.
5. **Round sounds** — `ui/util/RoundResolutionSoundEffect.kt` (mounted once in `RpsApp`) plays resolution audio from the active-match listener.
