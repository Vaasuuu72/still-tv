#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_device "${1:-}"
SNAPSHOT="${2:-}"
[[ -f "$SNAPSHOT/original-home.txt" && -f "$SNAPSHOT/serial.txt" ]] || { echo 'Usage: rollback.sh SERIAL BASELINE_DIRECTORY [--open|--restore-default]'; exit 2; }
[[ "$(cat "$SNAPSHOT/serial.txt")" == "$SERIAL" ]] || { echo 'Snapshot belongs to another connection; verify the device first.'; exit 2; }
original="$(tr -d '\r\n' < "$SNAPSHOT/original-home.txt")"
[[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ && "$original" != tv.still.launcher/* && "$original" != *ResolverActivity* ]] || { echo 'Invalid original HOME record.'; exit 2; }
case "${3:---show}" in
  --show)
    printf 'Original HOME: %s\nUse --open to test it without changing defaults.\nUse --restore-default only after approving a HOME change.\n' "$original" ;;
  --open) "$ADB" -s "$SERIAL" shell am start -W -n "$original" ;;
  --restore-default)
    "$ADB" -s "$SERIAL" shell cmd package set-home-activity --user 0 "$original"
    "$ADB" -s "$SERIAL" shell am start -W -n "$original"
    "$ADB" -s "$SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ;;
  *) echo 'Unknown option'; exit 2 ;;
esac
# Nothing is disabled or uninstalled, including Still TV.
