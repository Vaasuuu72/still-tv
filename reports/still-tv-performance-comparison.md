# Still TV vs Stock Android TV Home — Kodak Performance Comparison

Measured on the physical Kodak `PATH_UHDXANDROID` (Android 9, Amlogic txlx, Mali-450, 1920×1080), 2026-10-18 21:00–21:16 IST, TV uptime 1 h 01 min – 1 h 16 min, no reboot. Stock Android TV Home remained the default HOME throughout.

## Method

The same controlled method as the Phase 3A stock-launcher benchmark (`reports/phase3a/bench.sh`), extended only so it can bring a non-default launcher forward with `am start` instead of the Home key:

1. Bring the launcher forward and wait 6 s, then reset its gfx counters (frame statistics only).
2. Scripted D-pad navigation: 60 keys (right ×6, down, left ×6, … ×2), **never OK**, ~82 s.
3. Dump frame stats, framestats, launcher CPU ticks and meminfo.
4. Bring forward again, reset counters, **30 s idle**, dump again. GCs come from the launcher's own log, excluding "Explicit" GCs that `dumpsys meminfo` triggers.

**Runs were alternated** (Still TV 1, stock 1, Still TV 2, …) so uptime and thermal drift affect both launchers equally. Thermal sensors aren't readable on this firmware.

Raw data: `reports/phase3a/runs/p5-still-run{1,2,3}` and `p5-stock-run{1,2,3}` (in `~/projects/reports`).

## Per-run results

| Run | Nav frames | Janky % | p50 | p90 | p95 | p99 | Slow UI | Slow draw | Slow bitmap | Launcher CPU nav | Idle frames /30 s | Launcher CPU idle | PSS | Native heap | GCs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Still TV 1 | 224 | 11.6 | 9 | 18 | 27 | 38 | 0 | 0 | 0 | 4.2% | 1 | 0.1% | 17.3 MB | 4.8 MB | 0 |
| Still TV 2 | 207 | 4.3 | 6 | 11 | 15 | 22 | 0 | 0 | 0 | 2.6% | 0 | 0.0% | 17.7 MB | 5.0 MB | 0 |
| Still TV 3 | 207 | 0.5 | 5 | 9 | 13 | 15 | 0 | 0 | 0 | 2.2% | 0 | 0.0% | 18.2 MB | 5.2 MB | 0 |
| Stock 1 | 1101 | 17.3 | 7 | 21 | 25 | 65 | 20 | 12 | 6 | 21.9% | 1441 | 48.8% | 179.0 MB | 136.2 MB | 9 |
| Stock 2 | 1138 | 42.8 | 10 | 21 | 25 | 53 | 18 | 14 | 11 | 17.7% | 1456 | 48.7% | 178.7 MB | 136.5 MB | 8 |
| Stock 3 | 1130 | 40.9 | 9 | 22 | 25 | 48 | 15 | 14 | 9 | 16.9% | 1401 | 49.1% | 179.9 MB | 136.3 MB | 7 |

Frame times are in ms. CPU is % of one core.

Median frame phases (last ~120 navigation frames, ms):

| Launcher | UI thread | Sync/upload | Issue draw | Swap/GPU wait | Total |
|---|---:|---:|---:|---:|---:|
| Still TV (runs 1–3) | 2.8 / 1.9 / 0.8 | 0.3 | 2.3–2.6 | 1.3–1.4 | 8.6 / 7.3 / 6.3 |
| Stock (runs 1–3) | 1.6 / 2.1 / 1.3 | 0.5–0.8 | 2.7–4.0 | 1.6–1.7 | 9.3 / 11.3 / 9.6 |

## Summary (means of 3 runs)

| Metric | Still TV | Stock Android TV Home | Difference | Confidence |
|---|---:|---:|---|---|
| Launcher PSS | **17.8 MB** | 179.2 MB | **−90%** | High: ranges 17.3–18.2 vs 178.7–179.9 |
| Native heap (decoded images) | **5.0 MB** | 136.3 MB | **−96%** | High |
| Launcher CPU while navigating | **3.0%** | 18.8% | **−84%** | High: ranges don't overlap |
| Frames drawn while idle (30 s) | **~0** | 1,433 | — | High: stock banner rotation (see Phase 3A) |
| Launcher CPU while idle | **0.0%** | 48.9% | **−100%** | High |
| Whole-TV CPU busy while idle (4 cores) | **8.6%** | 26.8% | **−68%** | High |
| Launcher GCs per ~2-min run | **0** | 8 | — | High |
| p90 frame time (nav) | 12.7 ms | 21.3 ms | −40% | Moderate: Still TV run 1 (18 ms) overlaps the stock range |
| p95 frame time (nav) | 18.3 ms | 25.0 ms | −27% | Moderate: run 1 (27 ms) is a warm-up outlier |
| p99 frame time (nav) | 25.0 ms | 55.3 ms | −55% | Moderate |
| Janky frames (nav) | 5.5% | 33.7% | — | **Low comparability** (see below) |
| Slow UI / draw / bitmap frames per run | 0 / 0 / 0 | 18 / 13 / 9 | — | High (counts) |

### Start-up (Still TV only, `scripts/benchmark-tv.py`, 5 samples each)

| Mode | Median | Range | Notes |
|---|---:|---:|---|
| Cold (`am start -S`, Still TV force-stopped) | **1,082 ms** | 927–1,207 ms | First-ever launch after install: 1,557 ms |
| Warm (task recreated, process retained) | **470 ms** | 466–600 ms | All 5 samples kept the same PID |
| Hot re-entry | **not measurable** | — | `am start` returns no timing for an already-resumed activity. The script prints 0, which must not be read as 0 ms |

**The stock launcher's cold start was not re-measured.** Force-stopping the default HOME would disturb the system. The only stock figure available is its **6.5 s** cold start at boot (Phase 2, `am_activity_launch_time`), measured under boot load, so it isn't directly comparable.

## What these numbers do and don't mean

- **Different UI structures.** Still TV has 9 tiles plus a 3-button footer and animates only a 110 ms focus scale. Stock Home has a full-screen artwork banner, several image rows, chips and a sponsored row. **Still TV draws ~5× fewer frames for the same 60 key presses** (213 vs 1,123), because it has nothing to animate between keys. Frame-time percentiles compare *how expensive each frame is*, not identical work.
- **Janky % isn't a fair head-to-head.** The denominators differ by 5×, and stock janky % swings 17–43% between identical runs depending on the banner. Treat it as descriptive only.
- **Stock banner state:** all three stock runs had the rotating banner (≈1,430 idle frames), the state seen in most Phase 3A/3B sessions. In its occasional "quiet" state, stock idle cost would be lower (Phase 3B: ~120 frames, 11% CPU), but memory stays above 100 MB.
- **"High input latency" counts** (Still TV 213 of 213 frames, stock 857 of 1,123) mostly reflect the `input` tool's injected event timestamps, which predate delivery. They aren't evidence of remote lag either way and aren't used here.
- **Navigation response** in human terms (remote press → visible highlight) can't be measured without high-speed video. The frame data shows Still TV's median frame finishing in 6–9 ms (within one 60 Hz frame), versus 9–11 ms for stock.
- Still TV's PSS was 17–18 MB in the benchmark and 24–26 MB during the stability test, after repeatedly launching heavy apps (PSS shares change with what else is resident). Both are far below stock.

## Conclusion

On this TV, Still TV uses **about a tenth of the stock launcher's memory**, **almost no CPU while idle** (stock: about half a core), does **no image GC churn**, and its frame-time tail (p90/p99) is clearly lower. Those are measured results.

What these numbers don't show is how much faster it *feels* on the remote. You can only judge that by using it, which is part of the acceptance checklist.
