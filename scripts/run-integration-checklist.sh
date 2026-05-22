#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/functions"

echo "Building Cloud Functions..."
npm run build
npm test

echo ""
echo "Integration test checklist (requires Firebase project + 2 clients):"
echo "  [ ] Deploy: firebase deploy --only functions,firestore:rules,firestore:indexes"
echo "  [ ] Sign in with Google on two devices/emulators"
echo "  [ ] Both tap Find Match and get paired"
echo "  [ ] Play BO3 match; verify round scores and winner"
echo "  [ ] Confirm ELO changes on result screen and leaderboard"
echo "  [ ] Cancel matchmaking from one client while searching"
echo "  [ ] Let round timeout to verify forfeit resolution"
