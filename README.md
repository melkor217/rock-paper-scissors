# RPS Online

Online rock–paper–scissors for Android. Match with another player in real time, play best-of-3 rounds, climb an ELO leaderboard, and review match history on player profiles.

## How it works

- **Matchmaking** — queue until an opponent is found; pairing prefers players within **±200 ELO**.
- **Rounds** — each player picks rock, paper, or scissors within **60 seconds**. Same move is a draw and the round is replayed with no point awarded. A short pre-game countdown runs before the first round.
- **Match** — first to **2** round wins takes the match (best of 3).
- **Rating** — ELO updates after each completed match.
- **Profiles** — ELO, wins/losses, throw breakdown (rock / paper / scissors), and match history with per-round recaps. Your profile shows your last **10** matches; other players’ profiles show matches you played together. Open profiles from Home, the leaderboard, or the result screen.
- **Leaderboard** — ranked players with win/loss and throw tendencies; tap a row to open their profile.
- **Sign-in** — Google, email/password, or guest (see [scripts/ENABLE_AUTH.md](scripts/ENABLE_AUTH.md) for Firebase setup).
- **Updates** — release builds can check [GitHub Releases](https://github.com/melkor217/rock-paper-scissors/releases) from Home and install a newer APK in-app (debug builds skip this).

## Download

Pre-built APKs are attached to [GitHub Releases](https://github.com/melkor217/rock-paper-scissors/releases).

## Project layout

| Path | Purpose |
|------|---------|
| `app/` | Android client (Kotlin, Jetpack Compose) |
| `functions/` | Firebase Cloud Functions (match logic, timeouts, ELO) |
| `firestore.rules` | Firestore security rules |
| `firestore.indexes.json` | Composite indexes for queries |
| `docs/` | Architecture notes and GitHub Pages assets (e.g. App Links) |
| `scripts/` | Deploy, auth, backfill, and environment helpers |

See [docs/STRUCTURE.md](docs/STRUCTURE.md) for packages, Firestore paths, screen flow, and how client and backend interact.

## Build the app (local)

Requirements: **JDK 17**, **Android SDK (API 35)**, and a `google-services.json` from your Firebase project in `app/`.

`JAVA_HOME` is optional: `./gradlew` auto-detects Android Studio’s bundled JBR on macOS/Windows (default install locations) before falling back to `java` on `PATH`.

```bash
./gradlew :app:assembleDebug
```

Release builds need a signing keystore; CI uses repository secrets (see [.github/workflows/android-release.yml](.github/workflows/android-release.yml)). Pushing a version tag on `main` (e.g. `v1.0.0`) publishes a signed release APK.

Optional: [scripts/setup-android-env.sh](scripts/setup-android-env.sh) installs SDK packages for CI-like local builds.

## Backend

Deploy Firestore rules, indexes, and Cloud Functions:

```bash
./scripts/deploy-backend.sh
```

Match rules in the app (`GameRules`) must stay aligned with `functions/` (wins to finish, round timeout). After rule changes, redeploy functions and ship an app update if the client copy changes.

Run Cloud Functions unit tests:

```bash
cd functions && npm test
```

Manual smoke checklist (two signed-in clients): [scripts/run-integration-checklist.sh](scripts/run-integration-checklist.sh).

## CI (pull requests)

The [Android APK](.github/workflows/android-apk.yml) workflow runs on every push and pull request: debug APK build, `:app:testDebugUnitTest`, and a **14-day** debug APK artifact. The required check name on GitHub is **build**.

| PR source | Behavior |
|-----------|----------|
| Branch on this repo | Uses `GOOGLE_SERVICES_JSON_BASE64` secret when set. |
| Fork | GitHub may require a maintainer to **approve** the workflow run first. Secrets are not exposed to forks; CI uses `app/google-services.json.example` so compile still runs. |

To block merges until CI passes, add a **required status check** for `build` on `main` (Settings → Rules → rulesets → *main*, or branch protection).

## Tests

Android unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Cloud Functions (round resolution, ELO):

```bash
cd functions && npm test
```

## License

MIT — see [LICENSE](LICENSE).
