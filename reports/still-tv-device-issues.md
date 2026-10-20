# Still TV on the Kodak — Device Issues and Limitations

Found during Phase 5 real-device testing (2026-10-18). **No code was changed in this phase.** Proposed fixes are for a separate, approved change.

| # | Issue | Severity | Evidence | Status / proposed fix |
|---|---|---|---|---|
| 1 | ~~Long-press OK unverified on the physical remote~~: **resolved, not a bug.** An *injected* long-press (`input keyevent --longpress 23`) launched the app, but **holding OK on the real remote opens the management dialog** (confirmed by you, 2026-10-18) | — | Injected long-press sends key-down/up faster than `AbsListView`'s 500 ms long-press timeout; a physically held button works | No fix needed. Menu also works |
| 2 | **Up on the top row jumps to the first tile** | Low (UX) | Row 0 col 2 + Up → row 0 col 0 | `setSelection((position - columns).coerceAtLeast(0))` clamps to 0. Fix: if `position < columns`, consume the key without moving |
| 3 | **Initial favourites are seeded alphabetically** (first 7): Crunchyroll, Droid Settings, E-Manual, F1 TV, Jellyfin, JioHotstar, Live Channels. **YouTube was not included**; vendor utilities were | Low (first-run UX) | Screenshot `02-still-home` | Works as designed. For this TV, consider seeding with streaming apps only, or ask on first run. YouTube has been pinned manually during testing |
| 4 | **Inputs are listed in firmware order** (HDMI1, HDMI2, AV1, AV2, HDMI3) | Cosmetic | Screenshot `15-inputs-dialog` | Optional: sort by label |
| 5 | **Back from an input** (Live TV app) **goes to Android TV Home**, not Still TV | Expected | HDMI1 → `com.android.tv/.MainActivity`, Back → stock Home | Correct while Still TV isn't the default HOME. Recheck after any HOME change |
| 6 | **Physical Home button goes to Android TV Home** | Expected (required in this phase) | Verified (HOME intent → `com.google.android.tvlauncher`) | Confirms the default is untouched |
| 7 | `benchmark-tv.py` reports **hot re-entry = 0 ms** | Tooling | `reports/benchmark-20261018T154517Z.md` | `am start` returns `TotalTime: 0` for an already-resumed activity; the summary should exclude zero values (its own notes already warn against reporting 0) |
| 8 | Project status reports were stale ("Kodak not connected") | Docs | `reports/tv-baseline.md` (now replaced by the real audit), `reports/performance-comparison.md` | Superseded by `still-tv-performance-comparison.md`; update `performance-comparison.md` and the README status line in a later docs change |
| 9 | `uiautomator dump` fails on this firmware ("null root node") | Tooling / test only | Repeated failures | Tests used `dumpsys activity top` view bounds instead. No user impact |
| 10 | **Hold-to-manage felt slow** (your feedback) | Low (UX) | System long-press 400 ms + dialog build + open animation | **Fixed (Phase 6):** Still TV detects the hold itself at 250 ms (`HoldGesture`, 8 unit tests) and shows the dialog without its open animation. Awaiting your feel test |
| 11 | **`set-home-activity` alone can't make Still TV default on this firmware** | High (for the HOME goal) | Android TV Home's HOME filter has system priority 2; third-party apps get 0; Android 9 picks the higher priority before the preferred default | **Resolved with approval:** Android TV Home disabled (`pm disable-user`), so Still TV is HOME. Reversible from Settings or ADB |
| 12 | **Two Still TV instances** after testing with `am start -n` while Still TV is HOME | Test-only | Home stack + a standard-stack task | Not seen in normal use (Home button only). Cleared by restarting Still TV. Tests should bring Still TV forward with the Home key |

## Not issues (checked)

- **Focus jumping to the footer "Help" button** was investigated in depth. The cause was (a) your remote presses during an early run and (b) a macOS `seq 1 0` loop bug **in my test script**. After fixing the script, Still TV's focus behaved correctly in every run, including 10/10 stability cycles.
- **Disabled apps** (ZEE5, Sony LIV) are correctly excluded from discovery.
- **No crashes, ANRs or process restarts** were observed during testing (crash buffer: 0 entries).

## Platform limitations (not fixable in Still TV)

- **Hardware inputs** open through the stock TV input app (`com.android.tv`) via the public passthrough intent. Still TV can't render inputs itself, and that's by design.
- Screenshots of HDMI/video layers are blank (protected video plane).
- The stock launcher's banner, sponsored row and Google feeds stay installed and keep their background services; Still TV only replaces what's on screen.
