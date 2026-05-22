# Matchmaking fix (Firestore path — no Callable IAM)

Callable Functions were blocked by Cloud Run IAM. The app now uses **Firestore** instead:

| Action | How it works |
|--------|----------------|
| Find Match | App writes `queue/{your-uid}` (Firebase Auth, same as ELO) |
| Matchmaking | Cloud Function **`onQueueEntry`** runs when queue doc is created |
| Pick move | App writes `matches/{id}/playerChoices/{your-uid}` |
| Resolve round | Cloud Function **`onPlayerChoice`** updates the match |

## Deploy (required once)

```bash
cd ~/Documents/rock-paper-scissors
firebase deploy --only functions,firestore:rules
```

## Run the app

1. Rebuild & reinstall the Android app  
2. Sign in → **Find Match**  
3. Second device/emulator also taps **Find Match** to get paired  

You should **not** see `UNAUTHENTICATED` on Find Match anymore.

## Optional cleanup

Old Callable functions (`joinQueue`, `leaveQueue`, `submitMove`) are unused. You can delete them in Firebase Console → Functions to avoid confusion.
