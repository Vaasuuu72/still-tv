#!/usr/bin/env python3
"""Read-only audit. Never starts/stops apps, clears logs or changes settings."""
import datetime
import pathlib
import re
import subprocess
import sys

adb, serial = sys.argv[1:]
root = pathlib.Path(__file__).resolve().parents[1]
stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
safe_serial = re.sub(r'[^A-Za-z0-9._-]', '_', serial)
out = root / 'reports' / 'raw' / f'{safe_serial}-{stamp}'
out.mkdir(parents=True, exist_ok=False)

def shell(command):
    try:
        p = subprocess.run([adb, '-s', serial, 'shell', command], capture_output=True, text=True, timeout=40)
        return p.stdout.strip() + (f'\n[exit {p.returncode}] {p.stderr.strip()}' if p.returncode else '')
    except subprocess.TimeoutExpired:
        return '[unavailable: command timed out after 40 seconds]'

props = ['ro.product.manufacturer', 'ro.product.brand', 'ro.product.model', 'ro.product.device',
         'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.display.id', 'ro.build.fingerprint',
         'ro.product.cpu.abilist', 'ro.product.cpu.abi', 'ro.hardware', 'ro.config.low_ram']
values = {p: shell('getprop ' + p) for p in props}
sections = {
    'display': 'wm size; wm density',
    'memory': 'cat /proc/meminfo; cat /proc/pressure/memory',
    'storage': 'df -k /data /storage/emulated/0',
    'home': 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'home-fallback': 'pm resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'homes': 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'preferred-activities': 'dumpsys package preferred-xml',
    'system-packages': 'pm list packages -s -f',
    'user-packages': 'pm list packages -3 -f',
    'disabled-packages': 'pm list packages -d',
    'packages-details': 'dumpsys package',
    'processes': 'ps -A; ps',
    'services': 'dumpsys activity services',
    'cpu': 'dumpsys cpuinfo; top -b -n 1',
    'memory-pressure': 'dumpsys meminfo; dumpsys activity processes',
    'activity': 'dumpsys activity activities',
    'errors': 'logcat -d -t 500 *:W',
    'crash-buffer': 'logcat -b crash -d -t 200',
    'animations': 'settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale',
    'tv-inputs': 'dumpsys tv_input',
}
results = {}
for name, command in sections.items():
    print(f'Collecting {name}…', flush=True)
    results[name] = shell(command)
    (out / f'{name}.txt').write_text(f'$ {command}\n{results[name]}\n')
(out / 'identity.txt').write_text('\n'.join(f'{k}={v}' for k, v in values.items()) + '\n')
component_pattern = r'^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'
home = next((line.strip() for key in ('home', 'home-fallback') for line in results[key].splitlines()
             if re.fullmatch(component_pattern, line.strip()) and 'ResolverActivity' not in line), '')
if home and not home.startswith('tv.still.launcher/'):
    (out / 'original-home.txt').write_text(home + '\n')
    package = home.split('/')[0]
    results['launcher-memory'] = shell('dumpsys meminfo ' + package)
    results['launcher-rendering'] = shell('dumpsys gfxinfo ' + package)
    for name in ('launcher-memory', 'launcher-rendering'):
        (out / f'{name}.txt').write_text(results[name] + '\n')
(out / 'serial.txt').write_text(serial + '\n')
report = ['# Emulator baseline — not Kodak TV evidence' if serial.startswith('emulator-') else '# TV baseline', '', f'Captured UTC: {stamp}', f'Device: `{serial}`', '',
          'Read-only collection. No package, setting or HOME changes were made.', '', '## Device identity', '',
          '| Property | Observed value |', '|---|---|']
report += [f'| `{k}` | {v.replace("|", "/")} |' for k, v in values.items()]
report += ['', f'Raw evidence: `{out.relative_to(root)}`. Logs can contain personal app/session information; kept out of Git.', '',
           f'Original resolved HOME: `{home or "UNRESOLVED — record manually before deployment"}`.', '',
           '## Display, memory and storage', '']
for name in ('display', 'memory', 'storage', 'animations'):
    report += [f'### {name}', '```text', results[name], '```', '']
report += ['## Software and performance evidence', '',
           'Full package inventories, component/permission details, services, processes, sampled CPU, memory, input services, preferred activities and recent warnings/crashes are in the raw evidence directory.', '',
           'Unavailable commands and permission denials remain in the evidence rather than being treated as empty results.', '',
           'Startup, grid latency, focus correctness, streaming/DRM and causal lag analysis require subsequent interactive tests. CPU is a sample; free RAM alone does not establish a performance problem. Rendering counters are historical, not a controlled benchmark.', '',
           '## Next action', '', 'Review identity and package evidence. Validate the original HOME and Settings fallback before installing or changing defaults. Classify packages only after inspecting purpose and dependencies.']
report_path = root / 'reports' / ('emulator-baseline.md' if serial.startswith('emulator-') else 'tv-baseline.md')
if report_path.exists() and 'Captured UTC:' in report_path.read_text():
    report_path = root / 'reports' / f'tv-audit-{stamp}.md'
report_path.write_text('\n'.join(report) + '\n')
print(f'Saved {report_path}\nEvidence: {out}')
