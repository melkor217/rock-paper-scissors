#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Backfilling users/*/lastSeen in the active Firebase project..."
echo "Requires Application Default Credentials (e.g. gcloud auth application-default login)."
echo ""

npm --prefix functions run backfill-last-seen -- "$@"
