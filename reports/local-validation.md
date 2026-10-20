# Local validation — 2026-10-18

## Artifact

- Application: `tv.still.launcher`, version `0.1.0` (1), debug build.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Size: **889,522 bytes** (about 869 KiB).
- SHA-256: `aa1788fb4cdcceb5451ead3dd792dcdecfc8a960892447b8a7dbb9bf0d5d02b3`.
- `aapt dump permissions`: **no requested permissions**.
- Manifest inspection: HOME, standard launcher and leanback launcher entry points; minimum API 21, target 35; touch optional.

## Build and static checks

`./scripts/build.sh :app:assembleDebugAndroidTest` completed successfully. All **6 unit tests passed**. Android Lint: **0 errors, 6 warnings**. The warnings concern target/Gradle upgrades, an API-33-only manifest attribute ignored on older versions, a large but simple static banner vector, landscape orientation on future large-screen Android, and legacy backup configuration (allowBackup is already false). No lint baseline suppresses errors.

Shell syntax checks passed for every script. Python audit and benchmark collectors compile successfully. Unit results: `app/build/test-results/testDebugUnitTest/TEST-tv.still.launcher.FavouriteOrderTest.xml`; lint details: `app/build/reports/lint-results-debug.html`.

## Android TV emulator integration

Dedicated ARM64 Android TV emulator, Android 12/API 31 (`sdk_google_atv64_arm64`), 1920×1080, density 320, configured RAM 1536 MiB. Software renderer on the Mac; **not a hardware or performance proxy for the Kodak**. The image was installed because no complete AVD/image was initially available. Start again with `scripts/start-emulator.sh`; the AVD/data are local and ignored by Git.

`./scripts/test-emulator.sh emulator-5554`: **11 checks passed**, instrumentation result code `-1` (success). See [raw test transcript](emulator-smoke.txt).

1. Startup and initial focus.
2. All apps opened through D-pad OK.
3. D-pad movement and nonblank icon/fallback rendering.
4. Actual long-press management and Back.
5. Pin/unpin and preferences reloaded through a new repository.
6. Reorder earlier and later through actual remote menu actions.
7. Settings launched from its app tile; explicit return to Still TV restores the selected app.
8. Back from All apps restores home selection.
9. Footer-to-grid focus.
10. System Settings footer launch and Back return.
11. Ten repeated Settings-open/Back-return cycles.

The test harness waits for dialog windows to close before the next key event. It uses instrumentation shell input only when sending Back to system Settings; the production app does not gain input-injection permissions. App-tile return is **explicit** because Still TV is intentionally not default HOME. The test does not prove vendor Home-key interception, default-HOME behavior, streaming playback or DRM.

Additional process-restart test: the stored favourites XML before and after force-stopping/restarting only Still TV was byte-identical. Saved evidence: `reports/raw/emulator-favourites-before.xml` and `emulator-favourites-after.xml`. Startup success is recorded in `emulator-restart.txt`; its single timing is **not** a benchmark.

## Visual and operational checks

The actual 1080p emulator screen was captured and visually inspected: edges fit, app labels/fallbacks are present, focus has a clear outline and All apps uses an original grid icon. [Screenshot](emulator-home.png). This screenshot contains only dynamically discovered emulator apps; it does not pretend to represent the Kodak’s installed apps.

The read-only audit ran successfully on the emulator and saved its own `emulator-baseline.md`, preserving the pending Kodak report. The collector correctly retained a permission-denied result for memory PSI. Original HOME was `com.google.android.tvlauncher/.MainActivity`.

Both `rollback.sh --open` and `install-tv.sh` were exercised on the emulator. The original launcher opened, and the debug APK installed and opened manually. Final HOME resolution still identified the original Google TV launcher. No default was changed and no package was disabled or removed. See `emulator-deployment.txt`.

## Crash/ANR evidence and limitation

The retained emulator crash buffer contains **one system Settings crash** at 18:28:22 local emulator time during an earlier app-tile/Back test: `com.android.tv.settings`, `IllegalStateException: Can't access ViewModels from detached fragment`, in the Settings suggestions service callback. That earlier test failed. The later 11-check run passed, including ten footer Settings/Back cycles; app-tile return is tested explicitly as described above. The system Settings crash has not been proven fixed and must not be presented as crash-free system validation. No Still TV process crash appears in the retained crash buffer. `dumpsys activity lastanr` reported no ANR since emulator boot. Evidence is in `reports/raw/emulator-crash-buffer-final.txt` and `emulator-last-anr.txt`.

The temporary emulator was shut down after testing to release Mac resources. The Kodak TV was never connected or modified.

## Still unverified

- Kodak identity, API, resources, native resolution/density and ADB support.
- Kodak physical remote, vendor HOME policy, input switching, Wi-Fi/Bluetooth/audio behavior.
- Netflix/other installed streaming apps, actual playback, DRM and long-duration stability.
- Physical default selection, rollback of a changed default, reboot persistence.
- Removed/disabled app UI on the Kodak; unit filtering covers missing IDs but is not a device integration test.
- Runtime behavior on API 21–30 and API 33+ (API guards are lint checked; local runtime testing was API 31).
- Cold/warm/grid/input performance comparison or any optimization benefit.

No Kodak performance figure or improvement has been inferred from emulator timings. The next action is to connect the physical TV and run the untouched baseline audit.
