#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then ADB="$(command -v adb || true)"; fi
[[ -n "$ADB" && -x "$ADB" ]] || { echo 'ADB not found. Set ADB or ANDROID_HOME.' >&2; exit 1; }
require_device() {
  SERIAL="${1:-}"
  [[ -n "$SERIAL" && "$SERIAL" != -* ]] || { echo 'Pass the exact TV serial/address from adb devices -l.' >&2; exit 2; }
  [[ "$("$ADB" -s "$SERIAL" get-state)" == device ]] || { echo 'TV is not connected and authorized.' >&2; exit 1; }
}
