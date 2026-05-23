# Enable additional sign-in methods

The app supports **Google**, **anonymous (guest)**, and **email/password**. Enable each provider in Firebase:

1. [Firebase Console](https://console.firebase.google.com) → your project → **Authentication** → **Sign-in method**
2. Turn on:
   - **Anonymous** — for “Continue as guest”
   - **Email/Password** — for email sign-in and registration
   - **Google** — already required for Google Sign-In

No code changes are needed after enabling providers. If a method is disabled, the app shows: *This sign-in method is not enabled in Firebase Console*.

## Notes

- Email/password fields use native **EditText** autofill hints (`username` + `newPassword` on Register) so Google Password Manager can offer **Suggest strong password** when you focus the password field.
- After successful email sign-up or sign-in, the app calls **Credential Manager** (`CreatePasswordRequest`) to show the system **Save password** dialog (requires Activity context).
- Ensure **Settings → Google → Autofill** (or your password manager) is enabled on the device.
- Optional display name is excluded from the autofill set so it does not break the username/password pair.

- Guest accounts get a random display name (`Guest abc123`). ELO and match history are tied to that Firebase UID.
- Email users need a password of at least 6 characters (Firebase default).
- Firestore rules only require `request.auth != null`, so all providers work with matchmaking and gameplay.
