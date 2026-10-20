# Still TV 2.0 — Performance on the Kodak

Measured 2026-10-18/19 on the physical Kodak `PATH_UHDXANDROID`, 23:52–00:07 IST, TV uptime 49–64 min, no reboot. The **2.0 preview** (`tv.still.launcher.preview`) and **production 1.0** (`tv.still.launcher`, the default HOME) were run **alternately in the same session** with the same method:
- `reports/phase3a/bench.sh`: 60 D-pad keys (~81 s), then 30 s idle
- gfx counters reset
- GCs from the launcher's own log
- 1.0 brought forward with the Home key, 2.0 with `am start`

Raw: `~/projects/reports/phase3a/runs/p7-still2-run{1,2,3}` and `p7-still1-run{1,2,3}`.

## Per-run results

| Run | Nav frames | Janky % | p50 | p90 | p95 | p99 | Slow UI | Launcher CPU nav | Idle frames /30 s | Launcher CPU idle | PSS | Native heap | GCs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 2.0 run 1 | 332 | 0.6 | 6 | 9 | 10 | 13 | 0 | 3.4% | 0 | 0.0% | 21.6 MB | 8.3 MB | 0 |
| 2.0 run 2 | 331 | 0.0 | 5 | 8 | 9 | 11 | 0 | 2.8% | 1 | 0.1% | 21.2 MB | 8.1 MB | 0 |
| 2.0 run 3 | 332 | 0.0 | 5 | 8 | 9 | 11 | 0 | 2.7% | 0 | 0.0% | 21.1 MB | 8.1 MB | 0 |
| 1.0 run 1 | 195 | 6.2 | 7 | 13 | 17 | 24 | 11 | 3.2% | 0 | 0.0% | 17.0 MB | 4.8 MB | 0 |
| 1.0 run 2 | 208 | 2.4 | 6 | 9 | 12 | 18 | 6 | 2.7% | 0 | 0.0% | 17.5 MB | 5.1 MB | 0 |
| 1.0 run 3 | 207 | 0.0 | 5 | 8 | 10 | 13 | 0 | 1.9% | 1 | 0.1% | 17.7 MB | 5.2 MB | 0 |

Frame times are in ms. CPU is % of one core.

Median frame phases (ms), last ~120 navigation frames:
- **2.0:** UI thread 0.7–0.8, upload 0.6–0.7, issue draw 2.4, GPU/swap 1.3–1.4, total 6.3
- **1.0:** UI thread 0.9–2.1, upload 0.2–0.3, issue draw 2.2–2.4, GPU/swap 1.4–1.5, total 6.0–7.3

## Summary (means of 3 runs)

| Metric | 2.0 preview | 1.0 | Change | Target |
|---|---:|---:|---|---|
| Launcher PSS | **21.3 MB** | 17.4 MB | **+3.9 MB (+22%)**, larger card artwork | < 30 MB ✅ |
| Native heap | 8.2 MB | 5.0 MB | +3.2 MB (banner bitmaps) | — |
| CPU while navigating | 3.0% | 2.6% | +0.4 pt (within run-to-run spread 1.9–3.4%) | close to 1.0 ✅ |
| CPU while idle | 0.0% | 0.0% | none | ~0% ✅ |
| Frames drawn while idle (30 s) | ~0 | ~0 | none; no idle rendering | ✅ |
| Frames for the same 60 keys | 332 | 203 | +64%: the 160 ms focus transition and scroll glide draw more frames per move | expected |
| Janky frames | 0.2% | 2.9% | lower | — |
| p50 / p90 / p95 / p99 | **5.3 / 8.3 / 9.3 / 11.7 ms** | 6.0 / 10.0 / 13.0 / 18.3 ms | **p99 −36%**, all within one 60 Hz frame (16.7 ms) | close to 1.0 ✅ |
| Launcher GCs per run | 0 | 0 | none | ✅ |
| Slow UI-thread frames per run | 0 | 5.7 | lower | — |

**Reading it honestly:**
- 2.0 draws **more frames** because of its longer, richer focus transition, yet **each frame is cheaper or equal**, and no frame exceeded 13 ms in any 2.0 run. Unlike 1.0, the 2.0 runs show no UI-thread spikes: every move is resolved by `FocusMap` directly instead of GridView selection and layout.
- The 2.0 tail numbers are **consistent across all three runs** (p99 11–13 ms). 1.0's improved over its runs (24 → 13 ms), so part of 1.0's higher mean is warm-up.
- Memory rose as predicted (bigger cards using banners) and stays well under target.
- Whole-TV CPU while idle was ~7% for both, except one 2.0 run at 13.5%. The launcher itself was at 0% then, so that was other background activity on the TV.

## Startup (2.0 preview, 5 samples each; only the preview was force-stopped)

| Mode | Median | Range | Notes |
|---|---:|---:|---|
| Cold (`am start -S`) | **899 ms** | 872–914 ms | 1.0's cold start was **1,082 ms** (927–1,207) in Phase 5. 1.0 isn't re-measured because force-stopping the default HOME is avoided |
| Warm (task recreated, same PID) | **420 ms** | 198–903 ms | Wide spread: the first warm samples were slower. 1.0: 470 ms (466–600) in Phase 5 |

## Stability

- **Launch and return:** all 5 protected apps launched from the preview's favourites row (1.18–1.45 s to visible) and returned to the preview with **the launched app's card still selected**.
- **10-cycle run:** the script compared each cycle against the wrong app name (zsh arrays start at 1), so its "launched" column is invalid and isn't used. Still valid from that run:
  - the **same preview process for all 10 cycles** (no restarts)
  - memory **21.2–21.7 MB** after the first cycle (+0.5 MB over 10 cycles, no runaway growth)
  - **0 crashes / ANRs** in the crash buffer
- A corrected rerun was **not done**, on your instruction.

## Not measured

- Navigation latency as felt with the physical remote (needs your hands-on check)
- 1.0 cold start in the same session (avoided because 1.0 is HOME)
- Thermal state (not readable on this firmware)
