# Still TV — Kodak Acceptance Report (Phase 5)

Device: Kodak `PATH_UHDXANDROID` / `LEDTVUHD`, Android 9 (API 28), `armeabi-v7a`, Mali-450, 1920×1080 @ 320 dpi, ADB `192.168.1.8:5555`.
Date: 2026-10-18. **Android TV Home remained the default HOME throughout. `set-home-activity` was not run; no preferred activities were changed; nothing was disabled or uninstalled.**

## 1. Build and test results

| Check | Result |
|---|---|
| Code review (MainActivity, AppRepository, FavouriteOrder, manifest, Gradle, scripts) | Compatible with API 28: API 24/28/33 calls version-guarded; Android 12+/13+ manifest attributes ignored on 9; no native code (ABI-neutral, runs on 32-bit ARM) |
| HOME / launcher declarations | `MAIN`+`HOME`+`DEFAULT` and `MAIN`+`LEANBACK_LAUNCHER`+`LAUNCHER` on `MainActivity` (singleTask); banner present. `aapt` shows both `launchable-activity` and `leanback-launchable-activity` |
| Clean rebuild | `BUILD SUCCESSFUL` (Gradle 8.13, AGP 8.13, Kotlin 2.1.20, JDK 21) |
| Unit tests | **6/6 pass** (`FavouriteOrderTest`) |
| Lint | **0 errors**, 6 warnings (target/Gradle version, API 31/33/36-only attributes, large banner vector), none relevant to Android 9 |
| Emulator smoke test (API 31 TV) | **11/11 checks pass**, 0 crashes/ANRs |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 867,554 bytes, SHA-256 `510decf9ca3d615dbe68b5468b01ee61da6ab3fca19bd8323e40c68ff71fe533`, min 21 / target 35 |
| Permissions / components | **No permissions**, **0 services, receivers or providers**, no network, no native libraries |
| Code changes needed for the Kodak | **None** |

## 2. Baseline and installation

- Read-only audit (`scripts/audit-tv.sh`, reviewed first): `reports/raw/192.168.1.8_5555-20261018T150029Z/` (separate from emulator snapshots).
  - Firmware `THOMSON_KODAK/LEDTVUHD/LEDTVUHD:9/PI/20210922:user/release-keys`
  - Screen 1920×1080 @ 320
  - Disabled: `com.sonyliv`, `com.graymatrix.did`
  - Original HOME `com.google.android.tvlauncher/.MainActivity`
  - Still TV not previously installed
- `scripts/rollback.sh … --show` correctly identified the original HOME (no changes).
- Installed with your approval via `scripts/install-tv.sh` at 20:33:39. HOME candidates afterwards: stock launcher (priority 2), Still TV (priority 0), FallbackHome (−1000). **HOME still resolves to the stock launcher, with no chooser.**

## 3. Real-device screenshots

In `reports/raw/kodak-phase5/screens/`. Screenshots taken *inside* apps or showing account or network names were deliberately deleted.

| File | Shows |
|---|---|
| `02-still-home.png` | Still TV "Your apps" at 1080p: crisp icons, focus outline, clock, footer |
| `03-all-apps.png` | All apps grid |
| `11-menu-dialog.png` | Management dialog via the Menu key |
| `12-pinned-youtube.png`, `14-moved-earlier.png` | Pin and reorder |
| `15-inputs-dialog.png` | Inputs: HDMI1, HDMI2, AV1, AV2, HDMI3 |
| `18-after-force-stop.png` | Favourites and selection persisted after a process kill |
| `launch-*-0-focused.png` / `launch-*-2-returned.png` | Tile selected before launch, and restored after return |

## 4. Navigation and app-launch results

| Test | Result |
|---|---|
| Discovery | **15 apps**, exactly matching the TV's enabled TV-launchable apps. ZEE5 and Sony LIV **not shown** (disabled) |
| Protected apps launched **from Still TV's grid (D-pad + OK)** | **YouTube ✅, JioHotstar ✅, Crunchyroll ✅, F1 TV ✅, Jellyfin ✅**: each visible ~1.2–1.3 s after OK |
| Return to Still TV | Back ×1 (Crunchyroll, Jellyfin, JioHotstar, YouTube after its own screens), Back ×2 (F1 TV). **Selected tile restored every time** |
| Chromecast | Not launched (background receiver); running and enabled throughout |
| D-pad up/down/left/right, footer ↔ grid | ✅ (minor: Up on the top row jumps to the first tile, issue #2) |
| OK | ✅ launches the focused app |
| Back | ✅ All apps → Your apps; dialogs close; Your apps keeps Still TV visible |
| Menu → manage dialog | ✅ Open / Pin / Unpin / Move earlier / Move later / App info |
| Long-press OK | ✅ **Physical remote:** holding OK opens the management dialog (confirmed by you). An *injected* long-press launches the app instead, which is a test-tool artifact |
| Pin, reorder | ✅ YouTube pinned; moved earlier and later; order persisted |
| Reopen after force-stop | ✅ favourites and selection persisted; cold relaunch 924 ms |
| Settings button | ✅ Android TV Settings in 1.28 s; Back returns to Still TV with focus kept |
| Inputs | ✅ Lists 5 hardware inputs via public `TvInputManager`. HDMI1 opened the stock TV input app (`com.android.tv`); no vendor services modified |
| Screen scaling / icons | ✅ 5-column grid at 1080p, no clipping, no blank tiles, icons sharp |

## 5. Performance (summary; details in `still-tv-performance-comparison.md`)

Means of 3 alternated runs each, same session and method:

| | Still TV | Stock Home |
|---|---:|---:|
| PSS / native heap | **17.8 / 5.0 MB** | 179.2 / 136.3 MB |
| CPU navigating / idle | **3.0% / 0.0%** | 18.8% / 48.9% |
| Idle frames per 30 s | **~0** | 1,433 |
| GCs per run | **0** | 8 |
| p90 / p95 / p99 frame time | **12.7 / 18.3 / 25.0 ms** | 21.3 / 25.0 / 55.3 ms |
| Cold / warm start | **1,082 / 470 ms** (median) | 6.5 s at boot (not comparable) |

## 6. Stability (10 open/return cycles)

All 10 launches succeeded and returned. **Same Still TV process throughout (no restarts)**, focus restored to the exact tile each time, PSS 24–26 MB (no growth trend over 10 cycles), **0 crashes, 0 ANRs**, no blank screens or missing icons. Log: `reports/raw/kodak-phase5/stability-results.txt`.

## 7. Remaining bugs and limitations

See `still-tv-device-issues.md`. Summary: Up on the top row jumps to the first tile; alphabetical first-run seeding skipped YouTube; inputs unsorted; hot re-entry timing not measurable.

## 8. Recovery readiness (verified 21:24)

| Check | State |
|---|---|
| ADB | Connected, authorized |
| HOME | `com.google.android.tvlauncher/.MainActivity`; no preferred-HOME entries; **physical Home key → Android TV Home** |
| Original launcher | Installed, enabled |
| Disabled packages | Unchanged: `com.sonyliv`, `com.graymatrix.did` |
| Protected apps | YouTube, JioHotstar, Crunchyroll, F1 TV, Jellyfin, Chromecast: all enabled; Chromecast running |
| Still TV | Installed (left installed, as instructed) |
| Rollback tooling | `rollback.sh` identifies the original HOME; `--restore-default` available for the future HOME phase |

## Phase 6 update (2026-10-18 22:37–22:50): Still TV is now the default home screen

- Hold-to-manage now opens at **250 ms** with no open animation (your request). Short press still launches apps.
- `set-home-activity` couldn't override Android TV Home's system priority, so **Android TV Home was disabled** (approved Option A).
- **HOME resolves to Still TV; the Home key opens it; Back from HDMI and from apps returns to it.** Protected apps and core services are enabled, with 0 crashes or ANRs.
- **Recovery verified twice:** re-enabling Android TV Home restores it as HOME immediately. Settings → Apps → Android TV Home → **Enable** exists for no-computer recovery.
- **Boot verified** (approved reboot, 23:02:54): FallbackHome → **Still TV at 23:03:10, drawn in 2.15 s** (stock launcher at boot: 6.5 s in Phase 2; single boot each). Favourites and selection restored; 0 crashes/ANRs; evidence `reports/raw/kodak-phase6/boot-check.txt`, screenshot `after-boot.png`.
- **Pending:** your feel test of the 250 ms hold.

## 9. Recommendation: making Still TV the default launcher

### **CONDITIONAL GO**

Still TV is functionally correct on the real Kodak, stable over repeated use, and measurably far lighter than the stock launcher (−90% memory, ~0 idle CPU versus half a core). Nothing about the hardware or firmware blocks it.

**Conditions before running `set-home-activity`** (each in the separate, approved HOME phase):

1. ~~Hold OK on the physical remote~~: **done, works.** Still open: your judgement of navigation feel, and one playback in YouTube or JioHotstar started from Still TV.
2. **Decide whether to fix issue #2 (Up on the top row) and the first-run seeding (#3) first.** Both are small, contained code changes.
3. **In the HOME phase itself:** set HOME, verify the physical Home key opens Still TV, verify Back from an input returns to Still TV, then test `rollback.sh --restore-default` **before** any reboot, and only then confirm boot persistence with one approved reboot.
