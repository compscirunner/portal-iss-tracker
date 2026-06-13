#!/usr/bin/env bash
# Build (optional), install, and launch the ISS Tracker on a Meta Portal over adb.
#
# Usage:
#   ./deploy.sh [-s SERIAL] [--build] [--apk PATH]
#
#   -s SERIAL   target a specific adb device (e.g. 192.168.0.36:5555 = aloha/Portal+)
#   --build     run ./gradlew assembleDebug first
#   --apk PATH  install a specific apk instead of the default debug output
#
# adb is found via $ADB, $ANDROID_HOME/platform-tools, or PATH.
set -euo pipefail

PKG="com.portal.isstracker"
APK="app/build/outputs/apk/debug/app-debug.apk"
SERIAL="${SERIAL:-}"
DO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--serial) SERIAL="$2"; shift 2;;
    --build)     DO_BUILD=1; shift;;
    --apk)       APK="$2"; shift 2;;
    -h|--help)   sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
    *) echo "unknown arg: $1" >&2; exit 1;;
  esac
done

ADB="${ADB:-}"
if [[ -z "$ADB" ]]; then
  if command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"
  elif [[ -x "$HOME/Android/Sdk/platform-tools/adb" ]]; then ADB="$HOME/Android/Sdk/platform-tools/adb"
  else echo "adb not found — set ADB=/path/to/adb" >&2; exit 1; fi
fi
ADB_ARGS=(); [[ -n "$SERIAL" ]] && ADB_ARGS=(-s "$SERIAL")

if [[ "$DO_BUILD" == 1 ]]; then
  echo ">> building…"
  ./gradlew assembleDebug
fi

[[ -f "$APK" ]] || { echo "APK not found: $APK (run with --build)" >&2; exit 1; }

echo ">> installing $APK"
"$ADB" "${ADB_ARGS[@]}" install -r "$APK"

echo ">> launching $PKG"
"$ADB" "${ADB_ARGS[@]}" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null

# Keep the panel awake while the tracker is on screen.
"$ADB" "${ADB_ARGS[@]}" shell svc power stayon true || true
echo ">> done."
