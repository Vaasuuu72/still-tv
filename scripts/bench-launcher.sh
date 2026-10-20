#!/usr/bin/env bash
# Launcher benchmark (UI spec §8.3): cold/warm start, 60 D-pad keys with frame percentiles, 30 s idle frames/CPU,
# PSS and GC count. Only Still TV packages are accepted; it starts the launcher explicitly and sends D-pad keys.
# It never changes HOME, disables or force-stops anything other than the named Still TV package.
# Usage: scripts/bench-launcher.sh SERIAL PACKAGE RUN_LABEL
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_device "${1:-}"
PKG="${2:-}"; LABEL="${3:-run}"
[[ "$PKG" == tv.still.launcher || "$PKG" == tv.still.launcher.preview ]] || { echo 'PACKAGE must be tv.still.launcher or tv.still.launcher.preview' >&2; exit 2; }
if [[ "$PKG" == tv.still.launcher && "$SERIAL" != emulator-* && "${ALLOW_PRODUCTION_COLD_START:-0}" != 1 ]]; then
  echo 'Refusing to force-stop production Still TV (the active HOME) on a TV. Set ALLOW_PRODUCTION_COLD_START=1 only with approval.' >&2; exit 2
fi
COMPONENT="$PKG/tv.still.launcher.MainActivity"
OUT="$ROOT/reports/raw/still-tv-2.1/bench-$(echo "$SERIAL" | tr ':.' '__')-$PKG-$LABEL"
mkdir -p "$OUT"
sh() { "$ADB" -s "$SERIAL" shell "$@"; }
sh getprop ro.build.fingerprint > "$OUT/device.txt"
# Cold start (process killed), then warm (process alive, task brought to front).
sh am force-stop "$PKG"; sleep 2
sh am start -W -n "$COMPONENT" > "$OUT/cold-start.txt"; sleep 4
# Warm: leave via the Home key (whatever HOME is), then return to the still-running process.
sh input keyevent HOME; sleep 3
sh am start -W -n "$COMPONENT" > "$OUT/warm-start.txt"; sleep 3
# Navigation: 60 keys over ~60 s, same pattern every run.
sh dumpsys gfxinfo "$PKG" reset > /dev/null
PATTERN=(DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_LEFT DPAD_LEFT DPAD_DOWN DPAD_RIGHT DPAD_UP DPAD_UP)
for i in $(seq 0 59); do sh input keyevent "${PATTERN[$((i % 10))]}"; sleep 0.8; done
sleep 1
sh dumpsys gfxinfo "$PKG" > "$OUT/gfx-navigation.txt"
sh top -b -n 1 -o PID,%CPU,ARGS | grep -F "$PKG" > "$OUT/cpu-after-navigation.txt" || true
# Idle: 30 s with a top-row tile focused (navigate back to the first tile with Back).
sh input keyevent BACK; sleep 2
sh dumpsys gfxinfo "$PKG" reset > /dev/null
PID="$(sh pidof "$PKG" | tr -d '\r')"
T0="$(sh cat /proc/$PID/stat | awk '{print $14+$15}')"; sleep 30
T1="$(sh cat /proc/$PID/stat | awk '{print $14+$15}')"
sh dumpsys gfxinfo "$PKG" > "$OUT/gfx-idle.txt"
echo "cpu_ticks_idle_30s=$((T1 - T0))" > "$OUT/cpu-idle.txt"
sh dumpsys meminfo "$PKG" > "$OUT/meminfo.txt"
sh logcat -d -t 2000 | grep -E "^.{0,40} $PID .*(GC freed|paused)" > "$OUT/gc.txt" || true
python3 - "$OUT" <<'PY'
import pathlib, re, sys
out = pathlib.Path(sys.argv[1])
def grab(name, pattern):
    m = re.search(pattern, (out / name).read_text(), re.M); return m.group(1) if m else 'n/a'
nav = (out / 'gfx-navigation.txt').read_text()
summary = {
  'cold_start_ms': grab('cold-start.txt', r'TotalTime: (\d+)'),
  'warm_start_ms': grab('warm-start.txt', r'TotalTime: (\d+)'),
  'nav_frames': grab('gfx-navigation.txt', r'Total frames rendered: (\d+)'),
  'nav_janky': grab('gfx-navigation.txt', r'Janky frames: (\d+ \([\d.]+%\))'),
  'p50': grab('gfx-navigation.txt', r'50th percentile: (\d+)ms'),
  'p90': grab('gfx-navigation.txt', r'90th percentile: (\d+)ms'),
  'p95': grab('gfx-navigation.txt', r'95th percentile: (\d+)ms'),
  'p99': grab('gfx-navigation.txt', r'99th percentile: (\d+)ms'),
  'idle_frames_30s': grab('gfx-idle.txt', r'Total frames rendered: (\d+)'),
  'idle_cpu_ticks_30s': grab('cpu-idle.txt', r'cpu_ticks_idle_30s=(-?\d+)'),
  'pss_kb': grab('meminfo.txt', r'TOTAL(?: PSS)?:?\s+(\d+)'),
  'native_heap_kb': grab('meminfo.txt', r'Native Heap\s+(\d+)'),
  'gc_lines': str(len([l for l in (out / 'gc.txt').read_text().splitlines() if l.strip()])),
}
(out / 'summary.txt').write_text('\n'.join(f'{k}={v}' for k, v in summary.items()) + '\n')
print(' '.join(f'{k}={v}' for k, v in summary.items()))
PY
