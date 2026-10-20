# Performance comparison — physical measurements pending

No before/after measurements have been collected on the Kodak TV. No improvement is claimed.

| Metric | Original launcher / before | Still TV / after | Status |
|---|---|---|---|
| Cold start | Not measured | Not measured on TV | Pending connection |
| Warm task recreation | Not measured | Not measured on TV | Pending connection |
| Hot HOME reentry | Not measured | Not measured on TV | Pending connection |
| Apps grid ready | Not measured | Not measured on TV | Requires input/frame timing |
| Navigation responsiveness | Not measured | Not measured on TV | Requires actual remote/frame test |
| Available RAM | Unknown | Unknown | Pending read-only audit |
| Free internal storage | Unknown | Unknown | Pending read-only audit |
| Launcher process PSS | Unknown | Unknown | Pending device measurement |
| Crashes / ANRs | Not observed | Not observed on TV | No observation window yet |
| App-launch/playback reliability | Not tested | Not tested on TV | Streaming and DRM checks required |

Use `scripts/benchmark-tv.py` and the procedure in `docs/deployment.md`. Compare the same hardware, resolution, idle period, foreground workload and animation settings. Use at least seven samples per mode and report median/range, failed samples and uncertainty. Preserve raw evidence. Debug APK timings differ from optimized release builds.

`am start -W` reports Activity display timing, not completion of asynchronous discovery/icons. Warm tests require a stable PID. Automatic HOME restart may invalidate cold samples. ADB command latency is not remote input latency. Existing gfxinfo or crash-buffer history must not be presented as events from a controlled test without timestamps.

Local APK size, unit tests, lint and emulator behavior are tracked separately in `reports/local-validation.md`. They do not establish physical performance improvements.
