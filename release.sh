#!/usr/bin/env bash
# Build the signed release APK as `space-portal.apk` — the stable asset name the
# immortal App Store serves from releases/latest/download/space-portal.apk.
#
# Usage:
#   ./release.sh             # build space-portal.apk (signed with the release key)
#   ./release.sh upload      # build, then upload to the latest git tag's GH release
#   TAG=v1.1 ./release.sh upload
#
# Requires keystore.properties (local, gitignored) pointing at the release keystore.
set -euo pipefail
cd "$(dirname "$0")"

[ -f keystore.properties ] || { echo "keystore.properties missing — can't sign a release" >&2; exit 1; }

./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk space-portal.apk
echo ">> built space-portal.apk ($(du -h space-portal.apk | cut -f1))"

# Print the signer cert so you can confirm it's the stable release key.
APKSIGNER=$(ls "$HOME"/Android/Sdk/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
[ -n "$APKSIGNER" ] && "$APKSIGNER" verify --print-certs space-portal.apk | grep -iE "signer .*SHA-256|SHA-256" | head -1 || true

if [ "${1:-}" = "upload" ]; then
  TAG="${TAG:-$(git describe --tags --abbrev=0)}"
  gh release upload "$TAG" space-portal.apk --clobber
  echo ">> uploaded space-portal.apk to release $TAG"
fi
