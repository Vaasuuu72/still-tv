#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_device "${1:-}"
exec python3 "$ROOT/scripts/audit_tv.py" "$ADB" "$SERIAL"
