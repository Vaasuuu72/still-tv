# Connection, validation and recovery

## 1. Connect only the intended TV

On the Kodak, open Settings → Device Preferences or System → About. Record the Android version and build. Select Build repeatedly to enable Developer options, if not already enabled. Firmware menu names vary; inspect what this specific TV exposes. Record its Wi-Fi IP from network settings. The Mac and TV must be able to reach one another on the local network.

- If **Wireless debugging** has pairing, use its pairing address/port with `scripts/connect-tv.sh pair IP:PAIR_PORT`. Enter the short-lived code in the ADB prompt. Then use the separate connection address/port with `connect IP:DEBUG_PORT`. Pairing and connection ports are often different.
- If **Network debugging/ADB** displays or documents a TCP endpoint, enable it and use that endpoint. Do not assume 5555.
- If only **USB debugging** exists, first verify whether this TV exposes a supported USB device/debug port. Normal TV USB-A ports are usually host ports; do not improvise a USB-A-to-A cable. With a supported physical connection, authorize this Mac on the TV and inspect `connect-tv.sh list`. Then `connect-tv.sh usb-to-tcp AUTHORIZED_USB_SERIAL CHOSEN_PORT` can request TCP mode where firmware supports it.
- An `unauthorized` device requires accepting the RSA prompt on the TV. Check that it is the expected Mac. ADB cannot bypass authorization.
- If neither networking nor a supported USB debug connection is exposed, report the firmware restriction. Do not use exploits, root or blind port scanning.

Android’s official pairing workflow supports TV from Android 13/API 33; individual older vendor firmware can differ. Verify the actual menu instead of inferring support from phone Android versions. [Official ADB documentation](https://developer.android.com/tools/adb).

## 2. Capture the untouched baseline

Run `scripts/audit-tv.sh SERIAL`. Inspect device identity and all errors/unsupported commands. Record the resolved original HOME and candidate HOME activities. If resolution returns a chooser or is unavailable, manually identify the existing launcher and save its verified component; do not invent one. Review RAM, `/data` space, CPU, services, processes, crash logs and launcher frame data. No package changes are made by the collector.

Before measuring, let the TV sit idle for two minutes and note resolution, animation scales, temperature if available, network state, active media and recent boot/install activity. Use the same state for before/after tests. Do not kill streaming or essential services to improve numbers.

## 3. Test fallback and install

1. Test `scripts/rollback.sh SERIAL SNAPSHOT --open`; visually verify the original home renders and accepts the remote.
2. Open existing Settings and verify the remote’s Home button can reach the original launcher.
3. After local build/unit/lint checks, run `scripts/install-tv.sh SERIAL APK SNAPSHOT`.
4. Verify Still TV launches manually. It is not made default by installation.

## 4. Physical Kodak acceptance checklist

Record each result and any crash/ANR in the test report; none can be inferred from emulator success.

- [ ] Native resolution/density detected; edges and text fit the TV, including overscan.
- [ ] Cold and repeated startup; every installed launchable app appears once.
- [ ] Every visible app has its own icon or a readable fallback; no blank tiles.
- [ ] Arrows, rapid repeats, row boundaries, partial final rows and footer transitions behave predictably.
- [ ] OK launches; hold OK and Menu open management; dismissing restores selection.
- [ ] Pin/unpin, empty favourites, both reorder directions; force-stop/reopen confirms persistence.
- [ ] Returning from each installed streaming app restores the expected selection.
- [ ] Test actual playback, audio, DRM-protected video and remote controls in installed streaming apps. Opening an app alone does not validate DRM.
- [ ] System Settings, Wi-Fi, Bluetooth and audio remain available.
- [ ] HDMI/input list and switching tested if exposed; otherwise record the limitation and remote fallback.
- [ ] Package removal/disable handling tested with a disposable test app only; never disable a system dependency just to test.
- [ ] At least 10 app-open/home-return cycles without crash, ANR or lost focus.
- [ ] Original launcher fallback tested again after installation.

## 5. Default HOME requires explicit approval

After physical validation, inspect the standard Android Home-app selection screen (Settings → Apps → Default apps, where exposed). Present the verified Still TV component and original component to the user. Obtain confirmation before selecting Still TV.

If that UI is absent, inspect `cmd package help` / `pm help` and HOME resolution. A supported `set-home-activity` command may be considered after confirmation; success must be checked by resolving HOME and pressing the actual remote Home key. An API response alone is insufficient if the vendor intercepts the key.

Never disable/uninstall the original launcher to force selection. If the firmware restricts HOME replacement, supported fallback is manually launching Still TV from the existing launcher (and pinning that entry if the original launcher supports it). Do not install a key interceptor or accessibility-based workaround without separate discussion.

Test restoration through standard Home-app settings or the recorded `rollback.sh --restore-default` path **before** a reboot. Reboot only when HOME behavior and fallback are verified; then check boot persistence, Settings and streaming again. Do not assume network ADB persists across reboot.

## 6. Package optimization requires a separately approved list

Use `reports/bloatware-audit.md`. Evaluate actual package components, permissions, shared UIDs, libraries, active services and observed dependencies. Document a purpose, evidence, expected measurable benefit, risk and reversal for every candidate. Keep uncertainty in Group B. Never disable DRM, OTA, hardware, security, permissions or core Google/platform dependencies.

Only execute `pm disable-user --user 0 PACKAGE` for individually approved candidates, in small batches with playback, Settings, input and remote regression checks. Restore with `pm enable --user 0 PACKAGE`. User-0 uninstall requires separate approval plus a verified recovery strategy. No optimization is pre-approved by these instructions.

Animation changes, if worthwhile, also need recorded original values, measured benefit and a clear reversal. Still TV already avoids continuous/heavy rendering; there is no blanket animation override or cache-clearing procedure.

## 7. Repeatable measurements

The helper changes the foreground app and force-stops only the explicitly supplied launcher during cold runs. Run it when TV use can be interrupted:

```sh
python3 scripts/benchmark-tv.py --adb "$HOME/Library/Android/sdk/platform-tools/adb" \
  --serial SERIAL --component tv.still.launcher/.MainActivity --samples 7 --run-interactive
```

For the original launcher, supply its recorded component and `--original-home-file SNAPSHOT/original-home.txt`. Collect the baseline before optimization. Raw timestamps, PIDs and outputs remain available. Treat unsupported timings as unavailable, not zero. Warm task recreation requires the process PID to remain unchanged. Cold-start testing of a selected HOME can be contaminated by automatic restart; prefer manual/non-default tests and inspect launch state.

Measure grid readiness and remote latency separately using input-to-frame traces or high-speed video. Compare medians/ranges across equivalent conditions; Wi-Fi ADB round trips do not equal remote responsiveness. Capture idle memory/free storage and launcher PSS after the same idle duration. Review crash/ANR logs within the test time window. Update performance-comparison.md only with observed results.
