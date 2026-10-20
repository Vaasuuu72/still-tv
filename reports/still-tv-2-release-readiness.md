# Still TV 2.0 — Release Readiness

**Status: preview installed on the TV and device-tested (2026-10-18/19). Production Still TV 1.0 is still the default HOME and unchanged.** Results: `still-tv-2-performance.md`.

## Current production state (recorded 2026-10-18 23:28)

| Item | Value |
|---|---|
| Installed | `tv.still.launcher` versionCode 1, versionName 0.1.0 ("Still TV 1.0") |
| Role | Default HOME (`tv.still.launcher/.MainActivity`) |
| Installed APK SHA-256 | `ed03a82a03d655a31b341d21f7da2694072ab2862150e1913f1876c5c1648245` |
| Backup | `release/1.0-production/still-tv-1.0-installed.apk` (pulled from the TV, hash matches) + source snapshot |
| Signer | `CN=Android Debug`, SHA-256 `87:78:10:52:D3:11:5E:DA:4D:96:90:2E:11:08:E9:96:96:F4:68:0F:E0:D5:1B:D3:C7:34:11:36:C6:E7:D6:B4`. **Matches this Mac's `~/.android/debug.keystore`**, so 2.0 can update 1.0 in place, and 1.0 can be reinstalled |
| Fallback HOME | Android TV Home (`com.google.android.tvlauncher`) installed, **disabled-user**; system FallbackHome present. Enable/disable round-trip verified twice on 2026-10-18 |

## Artefacts

| File | Purpose |
|---|---|
| `release/2.0/still-tv-2.0-preview-debug.apk` (SHA-256 `a07dd358…80696e24`) | Side-by-side preview: `tv.still.launcher.preview`, **no HOME filter**. Installed on the TV |
| `release/2.0/still-tv-2.0-production-debug.apk` (SHA-256 `9ffdc0b1…508398a2`) | Production 2.0 (`tv.still.launcher`, versionCode 2) for the later update. **Not installed** |

## Step 1 — Preview on the TV (needs your approval)

```bash
adb -s 192.168.1.8:5555 install release/2.0/still-tv-2.0-preview-debug.apk
adb -s 192.168.1.8:5555 shell am start -n tv.still.launcher.preview/tv.still.launcher.MainActivity
```

Checks:
- HOME still resolves to `tv.still.launcher/.MainActivity` (production 1.0)
- the Home key still opens 1.0
- the preview finds the 15 apps and hides ZEE5 and Sony LIV
- default favourites appear
- protected apps launch and return
- Settings and HDMI inputs work
- you check how holding OK feels on the remote
- three benchmark runs against 1.0

Removing the preview at any time: `adb -s 192.168.1.8:5555 uninstall tv.still.launcher.preview`. It only affects the preview.

## Step 2 — Production update plan (separate approval, after the preview passes)

1. **Enable the fallback first**, so the TV is never without a working HOME during the update:
   `adb -s 192.168.1.8:5555 shell pm enable com.google.android.tvlauncher`. Android TV Home becomes HOME (its system priority wins); verify with the Home key.
2. **Update Still TV in place** (same package and signer; favourites are kept):
   `adb -s 192.168.1.8:5555 install -r release/2.0/still-tv-2.0-production-debug.apk`
3. Open it directly (`am start -n tv.still.launcher/.MainActivity`), and repeat the smoke checks and protected-app launches.
4. **Make it HOME again:** `adb -s 192.168.1.8:5555 shell pm disable-user --user 0 com.google.android.tvlauncher`. Verify the Home key, Back from HDMI, one approved reboot, and that it starts up into Still TV.
5. Uninstall the preview.

## Rollback

| Situation | Command |
|---|---|
| 2.0 misbehaves after the update | `adb -s 192.168.1.8:5555 install -r -d release/1.0-production/still-tv-1.0-installed.apk` (downgrade 2 → 1 is allowed for this debuggable, same-signer build; favourites are kept) |
| No working home screen | `adb -s 192.168.1.8:5555 shell pm enable com.google.android.tvlauncher`, or on the TV: Settings → Apps → See all apps → Show system apps → **Android TV Home → Enable** |
| Remove the preview | `adb -s 192.168.1.8:5555 uninstall tv.still.launcher.preview` |

## Device test results (preview)

| Check | Result |
|---|---|
| Installed alongside 1.0 | ✅ HOME stayed `tv.still.launcher/.MainActivity`; the preview isn't a HOME candidate |
| Discovery | ✅ 16 cards: 15 TV apps + production Still TV 1.0 itself. ZEE5 and Sony LIV hidden |
| Default favourites | ✅ YouTube, JioHotstar, Crunchyroll, F1 TV, Jellyfin |
| Protected apps launch and return | ✅ all 5; the selected card is restored on return |
| Top-row Up | ✅ goes to the header pill (fix confirmed on the TV) |
| Settings / Inputs | ✅ Settings opens in 1.21 s and returns; Inputs lists AV1, AV2, HDMI1–3 (sorted) |
| Wordmark | ✅ restored to the spaced **S T I L L** from 1.0 (your request) |
| Performance | ✅ PSS 21.3 MB, idle CPU 0%, p99 11.7 ms (1.0: 18.3 ms), cold start 899 ms |
| Stability | ✅ same process across 10 cycles, memory flat, 0 crashes. The script's launch column was invalid and the corrected rerun wasn't done, on your instruction |

## Open items before production

- ~~Device test of the preview~~: done (above).
- Your remote-feel check of the 250 ms hold and the 160 ms focus transition.
- Verify the stored 1.0 favourites (8 apps, including vendor utilities) display well as a two-row dock, or decide whether to start 2.0 from the new default five.
