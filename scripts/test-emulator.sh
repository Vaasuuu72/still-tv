#!/usr/bin/env bash
# Emulator-only integration run. PREVIEW=1 tests the side-by-side preview package (tv.still.launcher.preview),
# leaving any installed production Still TV untouched.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_device "${1:-}"
[[ "$SERIAL" == emulator-* ]] || { echo 'This runner is restricted to emulators; validate the TV using docs/deployment.md.'; exit 2; }
if [[ "${PREVIEW:-0}" == 1 ]]; then
  FLAGS=(-Pstill.preview=true); PKG=tv.still.launcher.preview; NAME=still-tv-preview-debug
else
  FLAGS=(); PKG=tv.still.launcher; NAME=app-debug
fi
"$ROOT/scripts/build.sh" "${FLAGS[@]}" :app:assembleDebugAndroidTest
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/$NAME.apk"
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/$NAME-androidTest.apk"
output="$("$ADB" -s "$SERIAL" shell am instrument -w -r "$PKG.test/tv.still.launcher.SmokeInstrumentation")"
printf '%s\n' "$output"
[[ "$output" == *'INSTRUMENTATION_CODE: -1'* && "$output" == *'PASS'* ]]
