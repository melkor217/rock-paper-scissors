#!/bin/bash
# Fix "UNAUTHENTICATED" on Callable Functions when Cloud Run requires IAM invoker.
# Requires: gcloud CLI (brew install google-cloud-sdk)
set -euo pipefail

PROJECT="${1:-rps-online-9771e}"
REGION="${2:-europe-west1}"

echo "Granting public invoker on Cloud Run services for project $PROJECT ($REGION)..."

for SERVICE in ping joinMatchmakingQueue submitmatchmove; do
  echo "→ $SERVICE"
  gcloud run services add-iam-policy-binding "$SERVICE" \
    --project="$PROJECT" \
    --region="$REGION" \
    --member="allUsers" \
    --role="roles/run.invoker" \
    --quiet || echo "  (skip if service name differs — check Cloud Run console)"
done

echo "Done. Retry the in-app ping meter (signed in)."
