#!/usr/bin/env python3
"""Interactive launcher-only measurements. Changes foreground app, never HOME settings.
Cold samples force-stop only the explicitly supplied launcher. Warm samples recreate
its task and record PIDs so callers can reject samples without a retained process.
"""
import argparse
import datetime
import json
import pathlib
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--component', required=True)
parser.add_argument('--original-home-file', type=pathlib.Path)
parser.add_argument('--samples', type=int, default=7)
parser.add_argument('--run-interactive', action='store_true')
args = parser.parse_args()
if not args.run_interactive:
    parser.error('This brings apps to the foreground. Pass --run-interactive when ready.')
allowed = {'tv.still.launcher/.MainActivity'}
if args.original_home_file:
    allowed.add(args.original_home_file.read_text().strip())
if args.component not in allowed or not re.fullmatch(r'[\w.]+/[\w.$]+', args.component):
    parser.error('Component must be Still TV or the recorded original HOME.')
if not 3 <= args.samples <= 30:
    parser.error('Use 3–30 samples.')
package = args.component.split('/')[0]
def shell(command):
    return subprocess.run([args.adb, '-s', args.serial, 'shell', command], text=True,
                          capture_output=True, timeout=45).stdout.strip()
def pid():
    return shell('pidof ' + package)

stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'reports' / 'raw' / f'benchmark-{stamp}'
out.mkdir(parents=True)
record = {'serial': args.serial, 'component': args.component, 'timestamp_utc': stamp,
          'identity': shell('getprop ro.build.fingerprint'), 'samples': []}
record['before'] = {name: shell(command) for name, command in {
    'memory': 'cat /proc/meminfo', 'storage': 'df -k /data', 'cpu': 'dumpsys cpuinfo'}.items()}
for mode, flags in [('cold', '-S'), ('warm-task-recreation', '-f 0x10008000'), ('hot-reentry', '')]:
    for index in range(args.samples):
        before = pid()
        output = shell(f'am start -W {flags} -n {args.component}')
        row = {'mode': mode, 'index': index, 'pid_before': before, 'pid_after': pid(), 'raw': output}
        for field in ('ThisTime', 'TotalTime', 'WaitTime'):
            match = re.search(rf'^{field}: (\d+)$', output, re.M)
            row[field] = int(match.group(1)) if match else None
        row['status_ok'] = bool(re.search(r'^Status: ok$', output, re.M))
        record['samples'].append(row)
        print(mode, index + 1, row.get('TotalTime'), flush=True)
        time.sleep(2)
record['after'] = {name: shell(command) for name, command in {
    'memory': 'cat /proc/meminfo', 'storage': 'df -k /data',
    'launcher_memory': 'dumpsys meminfo ' + package,
    'rendering': 'dumpsys gfxinfo ' + package,
    'crashes': 'logcat -b crash -d -t 200'}.items()}
(out / 'measurements.json').write_text(json.dumps(record, indent=2) + '\n')
summary = ['# Launcher measurement run', '', f'Component: `{args.component}`; serial: `{args.serial}`; UTC: {stamp}.', '',
           '| Mode | Valid timing samples | Median TotalTime ms | Range ms |', '|---|---:|---:|---:|']
for mode in ('cold', 'warm-task-recreation', 'hot-reentry'):
    samples = [r['TotalTime'] for r in record['samples'] if r['mode'] == mode and r['status_ok'] and r['TotalTime'] is not None
               and (mode != 'warm-task-recreation' or (r['pid_before'] and r['pid_before'] == r['pid_after']))]
    summary.append(f'| {mode} | {len(samples)} | {statistics.median(samples) if samples else "N/A"} | {str(min(samples)) + "–" + str(max(samples)) if samples else "N/A"} |')
summary += ['', 'am start timings measure activity launch/display, not completion of asynchronous app discovery or user-perceived readiness. Warm samples are valid only with a retained PID. Hot reentry may return no timing and must not be reported as zero. A default HOME can restart after force-stop, contaminating cold samples; inspect raw LaunchState/PIDs and repeat with the launcher non-default if necessary.', '',
            'No grid-open or input-latency estimate is inferred from these timings. Measure those separately using timestamped input/frame traces or high-speed video. Historical gfxinfo/crash counters are not automatically attributed to this run. Emulator results do not establish TV performance.', '',
            f'Raw evidence: `{out.relative_to(root)}/measurements.json`.']
(root / 'reports' / f'benchmark-{stamp}.md').write_text('\n'.join(summary) + '\n')
print(out)
