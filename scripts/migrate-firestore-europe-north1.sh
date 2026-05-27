#!/usr/bin/env bash
# Copy Firestore (default) from nam5 → a new database in europe-north1 (Finland).
#
# Firestore location is immutable. This script:
#   1. Exports (default) to a US bucket (compatible with nam5)
#   2. Copies export files to an EU bucket (required for europe-north1 import)
#   3. Creates a new database in europe-north1
#   4. Imports into that database
#
# After import, you must cut over manually:
#   - Point Android + Functions at the new database ID
#   - Redeploy Cloud Functions in europe-north1 (same region as Firestore triggers)
#   - Deploy firestore.rules + firestore.indexes to the new database
#   - Optionally delete the old (default) database once verified
#
# Prerequisites: Blaze billing, gcloud auth, roles Owner or Datastore Import Export Admin.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-rps-online-9771e}"
PROJECT_NUMBER="${PROJECT_NUMBER:-1040843099416}"
SOURCE_DB="${SOURCE_DB:-(default)}"
TARGET_DB="${TARGET_DB:-europe-north1}"
TARGET_LOCATION="${TARGET_LOCATION:-europe-north1}"

US_BUCKET="${US_BUCKET:-${PROJECT_ID}-firestore-export-us}"
EU_BUCKET="${EU_BUCKET:-${PROJECT_ID}-firestore-import-eu}"
EXPORT_PREFIX="${EXPORT_PREFIX:-migrate-$(date +%Y%m%d-%H%M%S)}"

SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-firestore.iam.gserviceaccount.com"

usage() {
  cat <<EOF
Usage: $0 <phase>

Phases:
  export-us     Create US bucket, export (default) → gs://${US_BUCKET}/${EXPORT_PREFIX}
  copy-to-eu    Create EU bucket, copy export prefix US → EU
  import-eu     Create DB '${TARGET_DB}' in ${TARGET_LOCATION}, import from EU bucket
  status        List recent Firestore admin operations

Environment overrides: PROJECT_ID, TARGET_DB, TARGET_LOCATION, EXPORT_PREFIX, US_BUCKET, EU_BUCKET
EOF
}

grant_firestore_agent() {
  local bucket="$1"
  echo "Granting Firestore service agent access to gs://${bucket}..."
  gcloud storage buckets add-iam-policy-binding "gs://${bucket}" \
    --project="${PROJECT_ID}" \
    --member="serviceAccount:${SERVICE_AGENT}" \
    --role="roles/storage.admin" \
    --quiet
}

phase_export_us() {
  echo "Creating US export bucket (us-central1, compatible with nam5)..."
  if ! gcloud storage buckets describe "gs://${US_BUCKET}" --project="${PROJECT_ID}" &>/dev/null; then
    gcloud storage buckets create "gs://${US_BUCKET}" \
      --project="${PROJECT_ID}" \
      --location=us-central1 \
      --uniform-bucket-level-access
  fi
  grant_firestore_agent "${US_BUCKET}"

  echo "Starting export of database '${SOURCE_DB}'..."
  gcloud firestore export "gs://${US_BUCKET}/${EXPORT_PREFIX}" \
    --project="${PROJECT_ID}" \
    --database="${SOURCE_DB}" \
    --async

  echo ""
  echo "Export started. Watch progress:"
  echo "  gcloud firestore operations list --project=${PROJECT_ID}"
  echo ""
  echo "When done, run: EXPORT_PREFIX=${EXPORT_PREFIX} $0 copy-to-eu"
}

phase_copy_to_eu() {
  if [[ -z "${EXPORT_PREFIX:-}" ]]; then
    echo "Set EXPORT_PREFIX to the folder created by export (timestamp folder under the bucket)."
    exit 1
  fi

  echo "Creating EU import bucket (${TARGET_LOCATION})..."
  if ! gcloud storage buckets describe "gs://${EU_BUCKET}" --project="${PROJECT_ID}" &>/dev/null; then
    gcloud storage buckets create "gs://${EU_BUCKET}" \
      --project="${PROJECT_ID}" \
      --location="${TARGET_LOCATION}" \
      --uniform-bucket-level-access
  fi
  grant_firestore_agent "${EU_BUCKET}"

  echo "Copying gs://${US_BUCKET}/${EXPORT_PREFIX} → gs://${EU_BUCKET}/${EXPORT_PREFIX} ..."
  gcloud storage cp -r \
    "gs://${US_BUCKET}/${EXPORT_PREFIX}" \
    "gs://${EU_BUCKET}/${EXPORT_PREFIX}"

  echo ""
  echo "Copy complete. Run: EXPORT_PREFIX=${EXPORT_PREFIX} $0 import-eu"
}

phase_import_eu() {
  if [[ -z "${EXPORT_PREFIX:-}" ]]; then
    echo "Set EXPORT_PREFIX to the export folder name."
    exit 1
  fi

  if ! gcloud firestore databases describe --database="${TARGET_DB}" --project="${PROJECT_ID}" &>/dev/null; then
    echo "Creating database '${TARGET_DB}' in ${TARGET_LOCATION}..."
    gcloud firestore databases create \
      --project="${PROJECT_ID}" \
      --database="${TARGET_DB}" \
      --location="${TARGET_LOCATION}" \
      --type=firestore-native
  else
    echo "Database '${TARGET_DB}' already exists."
  fi

  echo "Deploy indexes to the new database (from repo root):"
  echo "  firebase deploy --only firestore:indexes --project ${PROJECT_ID}"
  echo "(Configure firestore.indexes target database in firebase.json if needed.)"
  echo ""

  echo "Starting import into '${TARGET_DB}'..."
  gcloud firestore import "gs://${EU_BUCKET}/${EXPORT_PREFIX}" \
    --project="${PROJECT_ID}" \
    --database="${TARGET_DB}" \
    --async

  echo ""
  echo "Import started. Watch: gcloud firestore operations list --project=${PROJECT_ID}"
}

phase_status() {
  gcloud firestore operations list --project="${PROJECT_ID}" --limit=10
}

main() {
  local phase="${1:-}"
  case "${phase}" in
    export-us) phase_export_us ;;
    copy-to-eu) phase_copy_to_eu ;;
    import-eu) phase_import_eu ;;
    status) phase_status ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
