# RPS Online

Online rock–paper–scissors for Android. Match with another player in real time, play best-of-3 rounds, and climb an ELO leaderboard.

## How it works

- **Matchmaking** — queue until an opponent is found.
- **Rounds** — each player picks rock, paper, or scissors within 60 seconds. Same move is a draw and the round is replayed with no point awarded.
- **Match** — first to **2** round wins takes the match (best of 3).
- **Rating** — ELO updates after each completed match.
- **Sign-in** — Google, email/password, or guest (see [scripts/ENABLE_AUTH.md](scripts/ENABLE_AUTH.md) for Firebase setup).

## Download

Pre-built APKs are attached to [GitHub Releases](https://github.com/melkor217/rock-paper-scissors/releases).

## Project layout

| Path | Purpose |
|------|---------|
| `app/` | Android client (Kotlin, Jetpack Compose) |
| `functions/` | Firebase Cloud Functions (match logic, timeouts) |
| `firestore.rules` | Firestore security rules |
| `scripts/` | Deploy, auth, and environment helpers |

See [docs/STRUCTURE.md](docs/STRUCTURE.md) for packages, Firestore paths, and how client and backend interact.

## Build the app (local)

Requirements: JDK 17, Android SDK (API 35), and a `google-services.json` from your Firebase project in `app/`.

`JAVA_HOME` is optional: `./gradlew` now auto-detects Android Studio's bundled JBR on macOS/Windows (if installed in default locations) before falling back to `java` on `PATH`.

```bash
./gradlew :app:assembleDebug
```

Release builds need a signing keystore; CI uses repository secrets (see [.github/workflows/android-release.yml](.github/workflows/android-release.yml)). Pushing a version tag on `main` (e.g. `v0.5.8`) publishes a signed release APK.

## Backend

Deploy Firestore rules and Cloud Functions:

```bash
./scripts/deploy-backend.sh
```

Match rules in the app (`GameRules`) must stay aligned with `functions/` (wins to finish, round timeout).

## CI (pull requests)

The [Android APK](.github/workflows/android-apk.yml) workflow runs on every push and pull request: debug APK build plus `:app:testDebugUnitTest`. The required check name on GitHub is **build**.

| PR source | Behavior |
|-----------|----------|
| Branch on this repo | Uses `GOOGLE_SERVICES_JSON_BASE64` secret when set. |
| Fork | GitHub may require a maintainer to **approve** the workflow run first. Secrets are not exposed to forks; CI uses `app/google-services.json.example` so compile still runs. |

To block merges until CI passes, add a **required status check** for `build` on `main` (Settings → Rules → rulesets → *main*, or branch protection).

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

## License

MIT — see [LICENSE](LICENSE).
