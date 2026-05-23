#!/usr/bin/env bash
# Generate an upload keystore for CI release APKs (run once).
set -euo pipefail

KEYSTORE="${1:-app/upload.keystore}"
KEYTOOL="${KEYTOOL:-$(command -v keytool || true)}"
if [[ -z "$KEYTOOL" && -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" ]]; then
  KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"
fi
if [[ -z "$KEYTOOL" ]]; then
  echo "keytool not found. Install JDK or Android Studio." >&2
  exit 1
fi

read -r -s -p "Keystore password: " STORE_PASS
echo
read -r -s -p "Confirm password: " STORE_PASS_CONFIRM
echo
if [[ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]]; then
  echo "Passwords do not match." >&2
  exit 1
fi

"$KEYTOOL" -genkey -v -keystore "$KEYSTORE" -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=RPS Online, OU=Mobile, O=RPS Online, L=Unknown, ST=Unknown, C=US"

echo
echo "Keystore: $KEYSTORE"
echo "Add GitHub Actions secrets:"
echo "  ANDROID_UPLOAD_KEYSTORE_BASE64  = base64 -i $KEYSTORE | pbcopy"
echo "  ANDROID_UPLOAD_KEYSTORE_PASSWORD"
echo "  ANDROID_UPLOAD_KEY_PASSWORD"
echo "  ANDROID_UPLOAD_KEY_ALIAS        = upload"
echo
echo "Register this SHA-1 in Firebase (Project settings → Android app → Add fingerprint):"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -alias upload -storepass "$STORE_PASS" | grep SHA1
