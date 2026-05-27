#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Building functions..."
npm --prefix functions run build

echo "Deploying Firestore rules, indexes, and Cloud Functions to rps-online-9771e..."
firebase deploy --only functions,firestore:rules,firestore:indexes

echo ""
echo "Deployed (region europe-west1):"
echo "  Firestore triggers: onQueueEntry, onPlayerChoice, onRoundTimeout"
echo "  Callable: ping"
echo "  Scheduled: resolveTimedOutRounds (deadline backstop), cleanupStale"
