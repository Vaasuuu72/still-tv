#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_device "${1:-}"
APK="${2:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
SNAPSHOT="${3:-}"
[[ -f "$APK" ]] || { echo 'Build the debug APK first.'; exit 1; }
[[ -f "$SNAPSHOT/original-home.txt" && -f "$SNAPSHOT/serial.txt" ]] || { echo 'Pass a baseline snapshot directory as argument 3. A verified original HOME is required.'; exit 2; }
[[ "$(cat "$SNAPSHOT/serial.txt")" == "$SERIAL" ]] || { echo 'Snapshot belongs to another connection. Re-audit this TV.'; exit 2; }
original="$(tr -d '\r\n' < "$SNAPSHOT/original-home.txt")"
[[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ && "$original" != tv.still.launcher/* && "$original" != *ResolverActivity* ]] || { echo 'Snapshot must identify the original vendor HOME.'; exit 2; }
api="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$api" =~ ^[0-9]+$ ]] && (( api >= 21 )) || { echo 'This build requires API 21+. Do not install; adapt the project first.'; exit 1; }
"$ADB" -s "$SERIAL" install -r "$APK"
launch_output="$("$ADB" -s "$SERIAL" shell am start -W -n tv.still.launcher/.MainActivity)"
printf '%s\n' "$launch_output"
[[ "$launch_output" == *'Status: ok'* ]] || { echo 'Installation finished but launch was not verified; inspect the output above.'; exit 1; }
echo 'Still TV opened manually. Original HOME and all packages remain enabled. Complete docs/deployment.md before changing HOME.'
