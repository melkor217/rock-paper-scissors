#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Building functions..."
npm --prefix functions run build

echo "Deploying Firestore indexes + Cloud Functions to rps-online-9771e..."
firebase deploy --only functions,firestore:indexes

echo ""
echo "Deployed callables (region us-central1): joinQueue, leaveQueue, submitMove"
echo "Scheduled: resolveTimedOutRounds, cleanupStale"
