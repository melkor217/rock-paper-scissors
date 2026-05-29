# Enable additional sign-in methods

The app supports **Google**, **anonymous (guest)**, and **email/password**. Enable each provider in Firebase:

1. [Firebase Console](https://console.firebase.google.com) → your project → **Authentication** → **Sign-in method**
2. Turn on:
   - **Anonymous** — for “Continue as guest”
   - **Email/Password** — for email sign-in and registration
   - **Google** — already required for Google Sign-In

No code changes are needed after enabling providers. If a method is disabled, the app shows: *This sign-in method is not enabled in Firebase Console*.

## Google Sign-In SHA-1 fingerprints

Release APKs from GitHub are signed with the upload keystore, **not** your local debug key. Google Sign-In fails with *No credentials available* until Firebase knows that certificate.

1. Firebase Console → **Project settings** → your Android app (`com.rpsonline.app`)
2. **Add fingerprint** for each signing key you use:
   - Local debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
   - CI release: run `scripts/setup-release-keystore.sh` (or use the upload keystore SHA-1 from whoever created it)
3. Wait a few minutes, then **download a fresh `google-services.json`** and update the `GOOGLE_SERVICES_JSON_BASE64` GitHub secret for CI.

The web client ID in `strings.xml` must stay the **Web client** from Firebase/Google Cloud (not the Android client ID).

## Credential Manager (save password)

**Optional.** The game works without Digital Asset Links — matchmaking, rounds, ELO, and all sign-in methods (Google, email/password, guest) are unaffected. Links are only needed if you want release builds to show the system **Save password** dialog after email sign-up or sign-in; without them, sign-in still succeeds and the app silently skips the save prompt.

Release builds need [**Digital Asset Links**](https://developer.android.com/identity/credential-manager/prerequisites#configure-digital-asset-links-for-passwords) to enable that prompt:

1. Enable **GitHub Pages** for this repo: **Settings → Pages → Build from branch → `/docs` → main**.
2. Confirm `https://melkor217.github.io/rock-paper-scissors/.well-known/assetlinks.json` returns JSON (200).
3. Add each signing certificate’s **SHA-256** to `docs/.well-known/assetlinks.json` (debug + upload keystore). Get upload SHA-256 with:
   `keytool -list -v -keystore app/upload.keystore -alias upload`

## Notes

- Email/password fields use native **EditText** autofill hints (`username` + `newPassword` on Register) so Google Password Manager can offer **Suggest strong password** when you focus the password field.
- After successful email sign-up or sign-in, the app calls **Credential Manager** (`CreatePasswordRequest`) to show the system **Save password** dialog (requires Activity context).
- Ensure **Settings → Google → Autofill** (or your password manager) is enabled on the device.
- Optional display name is excluded from the autofill set so it does not break the username/password pair.

- Guest accounts get a random display name (`Guest abc123`). ELO and match history are tied to that Firebase UID.
- Email users need a password of at least 6 characters (Firebase default).
- Firestore rules only require `request.auth != null`, so all providers work with matchmaking and gameplay.

## App Check (required when enforcement is on)

### Release policy (GitHub only)

This project ships **release APKs via [GitHub Releases](https://github.com/melkor217/rock-paper-scissors/releases)** — not Google Play. Sideloaded APKs cannot use **Play Integrity** App Check reliably, so **release builds keep the debug App Check provider enabled**:

- **Release builds:** `USE_DEBUG_APP_CHECK` is hardcoded `true` in `app/build.gradle.kts` (debug App Check provider).
- **CI:** [android-release.yml](../.github/workflows/android-release.yml) builds `assembleRelease` (same debug App Check behavior).
- **You must register** the upload-keystore debug token in Firebase (Logcat → **Manage debug tokens**) once per signing key.

Only switch to Play Integrity (`-PuseDebugAppCheck=false`) if you later publish the same signed app through Play Store and configure Play Integrity in Firebase.

If sign-in shows **App attestation failed** or **403 App Check**, Firebase is rejecting the app — not SHA-1 or Wi‑Fi.

### Debug build (Android Studio Run, emulator **or** physical phone)

1. Run the app once.
2. Logcat → filter **`DebugAppCheckProvider`** → copy the **debug secret** (one token per debug keystore; same token works on emulator and USB device).
3. [Firebase Console](https://console.firebase.google.com) → **App Check** → Android app `com.rpsonline.app` → **Manage debug tokens** → **Add** → paste → Save.
4. Force-stop the app and open it again.

### GitHub release APK (v0.6.x from Releases)

CI **release** APKs are **sideloaded**, not installed from Play Store. They use the **debug App Check provider** (not Play Integrity):

1. Install `rps-online-vX.apk` from [GitHub Releases](https://github.com/melkor217/rock-paper-scissors/releases).
2. Connect the phone to Android Studio → **Logcat** → filter **`DebugAppCheckProvider`**.
3. Open the app once; copy the line **"Enter this debug secret into the allow list…"**
4. Firebase → **App Check** → `com.rpsonline.app` → **Manage debug tokens** → **Add** → paste → Save.
5. **Force-stop** the app, wait a minute if you saw **Too many attempts**, then open again.

The upload-keystore release uses a **different** debug secret than your local `~/.android/debug.keystore` — register both if you test GitHub APK and Studio **Run release** on a device.

### Studio release on a physical device

**Run → release** from Android Studio also uses debug App Check. Register the token from Logcat on **that device** for **that build** (usually the local debug keystore unless CI upload keystore env vars are set).

### Play Store release (future)

When you ship through Google Play, build with Play Integrity (not the GitHub sideload path):

```bash
./gradlew :app:assembleRelease -PuseDebugAppCheck=false
```

Register **Play Integrity** in Firebase → **App Check** for `com.rpsonline.app` (app must be in Play Console with the same package name and signing key).

### CI

| Workflow | Gradle flag | App Check |
|----------|-------------|-----------|
| [android-release.yml](../.github/workflows/android-release.yml) (GitHub Releases) | `-PuseDebugAppCheck=true` | Debug token (Logcat → Firebase) |
| [android-apk.yml](../.github/workflows/android-apk.yml) | debug build only | Debug token |
| Play Store (not automated yet) | `-PuseDebugAppCheck=false` | Play Integrity |

For day-to-day dev, use a **debug** build from Android Studio and register that debug token as above.

### Temporary workaround (development only)

Firebase → **App Check** → **APIs** (Authentication, Cloud Firestore) → set enforcement to **Unenforced** (metrics only) until debug tokens or Play Integrity are configured. Turn enforcement back on before production.
