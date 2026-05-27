#!/usr/bin/env bash
# Copy Firestore database europe-west1 → (default) via export/import.
# Both databases must exist in the same location (europe-west1).
#
# Usage: ./scripts/copy-firestore-to-default.sh <export|import|status>
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-rps-online-9771e}"
PROJECT_NUMBER="${PROJECT_NUMBER:-1040843099416}"
SOURCE_DB="${SOURCE_DB:-europe-west1}"
TARGET_DB="${TARGET_DB:-(default)}"
BUCKET="${BUCKET:-${PROJECT_ID}-firestore-import-europe-west1}"
EXPORT_PREFIX="${EXPORT_PREFIX:-copy-to-default-$(date +%Y%m%d-%H%M%S)}"

SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-firestore.iam.gserviceaccount.com"

usage() {
  cat <<EOF
Usage: $0 <export|import|status>

  export   Export '${SOURCE_DB}' to gs://${BUCKET}/${EXPORT_PREFIX}
  import   Import into '${TARGET_DB}' (set EXPORT_PREFIX to the export folder name)
  status   List recent Firestore admin operations

Environment: PROJECT_ID, EXPORT_PREFIX, BUCKET, SOURCE_DB, TARGET_DB
EOF
}

grant_firestore_agent() {
  echo "Granting Firestore service agent access to gs://${BUCKET}..."
  gcloud storage buckets add-iam-policy-binding "gs://${BUCKET}" \
    --project="${PROJECT_ID}" \
    --member="serviceAccount:${SERVICE_AGENT}" \
    --role="roles/storage.admin" \
    --quiet
}

phase_export() {
  if ! gcloud storage buckets describe "gs://${BUCKET}" --project="${PROJECT_ID}" &>/dev/null; then
    echo "Creating bucket gs://${BUCKET} (europe-west1)..."
    gcloud storage buckets create "gs://${BUCKET}" \
      --project="${PROJECT_ID}" \
      --location=europe-west1 \
      --uniform-bucket-level-access
  fi
  grant_firestore_agent

  echo "Exporting '${SOURCE_DB}' → gs://${BUCKET}/${EXPORT_PREFIX} ..."
  gcloud firestore export "gs://${BUCKET}/${EXPORT_PREFIX}" \
    --project="${PROJECT_ID}" \
    --database="${SOURCE_DB}" \
    --async

  echo ""
  echo "Export started. Watch: $0 status"
  echo "When done, run: EXPORT_PREFIX=${EXPORT_PREFIX} $0 import"
}

phase_import() {
  if [[ -z "${EXPORT_PREFIX:-}" ]]; then
    echo "Set EXPORT_PREFIX to the export folder name under gs://${BUCKET}/"
    exit 1
  fi
  grant_firestore_agent

  echo "Importing gs://${BUCKET}/${EXPORT_PREFIX} → '${TARGET_DB}' ..."
  gcloud firestore import "gs://${BUCKET}/${EXPORT_PREFIX}" \
    --project="${PROJECT_ID}" \
    --database="${TARGET_DB}" \
    --async

  echo ""
  echo "Import started. Watch: $0 status"
}

phase_status() {
  gcloud firestore operations list --project="${PROJECT_ID}" --limit=10
}

main() {
  local phase="${1:-}"
  case "${phase}" in
    export) phase_export ;;
    import) phase_import ;;
    status) phase_status ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
