#!/bin/bash
# Grant roles/run.invoker to allUsers on HTTPS callable Cloud Run services.
# Fixes client "UNAUTHENTICATED" when Cloud Run IAM blocks unauthenticated invoke.
#
# Usage: ./scripts/fix-functions-iam.sh [PROJECT_ID] [REGION]
# Requires: gcloud CLI, logged in (gcloud auth login)
set -euo pipefail

PROJECT="${1:-rps-online-9771e}"
REGION="${2:-europe-west1}"

# Firebase Functions v2 deploy names are lowercase on Cloud Run.
CALLABLE_SERVICES=(
  ping
  touchpresence
  joinmatchmakingqueue
  submitmatchmove
)

echo "Granting public invoker on Cloud Run callables for project $PROJECT ($REGION)..."

failed=()
for SERVICE in "${CALLABLE_SERVICES[@]}"; do
  echo "→ $SERVICE"
  if ! gcloud run services add-iam-policy-binding "$SERVICE" \
    --project="$PROJECT" \
    --region="$REGION" \
    --member="allUsers" \
    --role="roles/run.invoker" \
    --quiet 2>&1; then
    failed+=("$SERVICE")
  fi
done

if ((${#failed[@]} > 0)); then
  echo ""
  echo "Failed (service missing or no permission): ${failed[*]}"
  echo "Deployed callables in $REGION:"
  gcloud run services list --project="$PROJECT" --region="$REGION" --format='table(metadata.name)' 2>&1 || true
  echo ""
  echo "Deploy missing functions first, e.g.:"
  echo "  firebase deploy --only functions:touchPresence,functions:joinMatchmakingQueue"
  exit 1
fi

echo "Done. Callable invoker IAM updated for: ${CALLABLE_SERVICES[*]}"
