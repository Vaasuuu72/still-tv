# Still TV 2.0 — Design and Implementation

Date: 2026-10-18 · Target: Kodak `PATH_UHDXANDROID` (Android 9, Mali-450 GLES 2.0, 1920×1080 @ 320 dpi = 960×540 dp)

## 1. Principles taken from tvOS, translated for this TV

Apple's HIG pages render with JavaScript and couldn't be fetched as text, so the tvOS figures below are the widely documented ones and should be treated as **unverified against the live HIG**. The 2025 newsroom article confirms Liquid Glass relies on real-time refraction, which this brief excludes.

| tvOS principle | Still TV 2.0 translation | Why it fits a Mali-450 |
|---|---|---|
| ~60 pt / 80 pt safe area on a 1920×1080 canvas | **5% margins** computed by `HomeLayout`: 48 dp sides, 27 dp top (96 px / 54 px) | Pure layout, no cost |
| Landscape app icons (400×240 pt, 5:3) in a dock row + grid | **16:9 cards**: Leanback banners are 16:9, so they fill a card with **no distortion**. Dock: 5 large cards; All Apps: 6 columns (5 on narrower screens) | Real app artwork; nothing recreated |
| Focus = lift: scale, shadow, light | **Scale 1.06**, a pre-drawn **soft shadow** and a **light edge with faint brightening** fade in, and the **name appears below**. No parallax, blur or animated shadow | Only scale and alpha change; hidden layers aren't drawn |
| Fluid, interruptible motion | **160 ms**, `PathInterpolator(0.2, 0, 0, 1)` (fast start, settle without overshoot, i.e. critically damped). `ViewPropertyAnimator` retargets from the **current on-screen value**, so rapid D-pad moves never jump | Two cards animate per move |
| Focus engine predictability | New pure **`FocusMap`**: deterministic up/down/left/right across header → dock → All Apps, **remembered** positions when re-entering a section, and column mapping by screen position | Unit-tested; no reliance on Android's geometric guesses |
| Dim to focus for modal tasks | Context menu and Inputs use a **dark rounded floating panel** over a 55% dim, with the focused row filled white. **No window animation**, so it appears instantly | One static panel |
| Restraint / simplicity | Header = wordmark, clock, **Inputs** and **Settings** pills. No weather, notifications, feeds or recommendations | — |
| Typography | Wordmark kept as the spaced **S T I L L** from 1.0 (24 sp, regular; your request), read aloud as "Still". Small hint text +0.01 em tracking; labels 15 sp medium | System font (Roboto) |
| Reduced motion | When the system animator scale is 0, focus and scrolling change **instantly** | — |

## 2. Screen composition (1080p, measured in dp)

```
 Still                                       11:42 PM   [ Inputs ] [ Settings ]   ← header, 56 dp
 ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
 │ banner │ │ banner │ │ banner │ │ banner │ │ banner │                  ← Favourites dock: 5 × 154×86 dp, 24 dp gaps
 └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
   name (focused only)
 All Apps                                         Hold OK to edit Favourites
 ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                   ← All Apps: 6 × 127×72 dp, 20 dp gaps
 └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘
```

- Card widths are computed so each row fills the safe width exactly. Focus growth (+4.6 dp per side on dock cards) stays inside the gaps (unit-tested).
- **Favourites aren't repeated** in All Apps. With more than 5 favourites, the dock wraps to a second row.
- **Default favourites** (only when none are saved): YouTube, JioHotstar, Crunchyroll, F1 TV, Jellyfin (installed ones only); otherwise the first five apps. The production TV already has saved favourites, which are kept.
- **Icon-only apps** (no banner) get a charcoal card with the icon and their name inside.
- **Background:** a static graphite gradient (`#16181D → #0D0E11`). No texture, animation or blur.

## 3. Architecture (native Views, no new dependencies)

| Component | Role | Status |
|---|---|---|
| `MainActivity` | Lifecycle, discovery refresh, rendering dock/grid, key routing (D-pad → `FocusMap`, OK → `HoldGesture`), menus, Settings/Inputs, Home/Back behaviour | Rewritten (GridView replaced) |
| `FocusMap` | Pure directional focus between sections | **New**, 13 unit tests |
| `HomeLayout` | Pure safe-area and card geometry from screen dp | **New**, 7 unit tests |
| `Tiles` | Builds cards, binds artwork, runs the focus transition | **New** |
| `CalmScrollView` | Keeps the focused card and its label 40 dp clear of edges; glides instead of jumping | **New** |
| `MenuPanel` | Floating panel for the context menu and Inputs | **New** (replaces AlertDialog) |
| `AppRepository` | Discovery (unchanged). **Artwork rendered once per app at card size** with rounded corners baked in; 8 MB LRU; default favourites | Modified |
| `FavouriteOrder` | Added `defaults()` and `rest()` | Modified, 9 tests |
| `HoldGesture` | 250 ms hold-to-manage (unchanged) | 8 tests |

**Performance design:**
- Artwork is decoded and rounded **once** per app, at its displayed size, on the worker thread, then cached. Focus changes never decode, clip or allocate bitmaps.
- No continuous rendering: the clock updates once a minute; nothing animates at rest.
- Still offline. **No new permissions**, no services, receivers or providers in the manifest, no network.

## 4. Fixed navigation issues

| Issue | Fix | Evidence |
|---|---|---|
| Up on the top row jumped to the first tile | Up from the top row enters the header; within a section Up keeps the column | `FocusMapTest`, emulator check |
| Rapid D-pad could leave focus inconsistent | Every key is resolved synchronously by `FocusMap` from the current focus | 5,000-move random unit test; 60 rapid keys on the emulator |
| Focus jumps after pin/unpin/reorder | Selection is protected while cards are rebuilt, then restored onto the same app | Emulator check |
| First launch focused the Inputs pill | The window's automatic first focus is ignored until apps have loaded | Emulator check |
| Crash if apps refresh while a pill is focused | Card focus effect only applies to cards | Found by the new test; 0 crashes since |
| Back could leave the launcher | Back walks focus to the first card and never finishes the activity | Emulator check |
| Home while already on Still TV | Returns to the first favourite; returning from an app keeps its card selected | Emulator check |

## 5. Preview build (deployment safety)

`./gradlew -Pstill.preview=true :app:assembleDebug` → `still-tv-preview-debug.apk`:
- application id **`tv.still.launcher.preview`**, label **"Still TV Preview"**, version `2.0.0-preview`
- the HOME category is replaced by a private `tv.still.launcher.category.PREVIEW_NOT_HOME`. Verified with `aapt`: the activity's filters are `MAIN+PREVIEW_NOT_HOME+DEFAULT` and `MAIN+LEANBACK_LAUNCHER+LAUNCHER`. **It can't become HOME.**
- its own preferences, so it starts with the default favourites and can't disturb production Still TV's data.

## 6. Tests

| Suite | Result |
|---|---|
| Unit (JVM) | **37/37 pass**: FocusMap 13, HomeLayout 7, FavouriteOrder 9, HoldGesture 8 |
| Lint | **0 errors**, 6 warnings (all from 1.0: target SDK/Gradle versions, API 31/33/36-only attributes, banner vector size) |
| Emulator instrumentation (API 31 TV, 11 check groups) | **3/3 runs pass**, 0 crashes |

The 11 emulator check groups:
1. default favourites with no duplicates
2. artwork and icon fallback on every card
3. directional focus including top-row Up
4. focused card scaled, others at rest
5. 60 rapid D-pad keys
6. hold-to-manage menu and Back
7. unpin, pin and reorder with stable focus
8. app launch and return with restored selection
9. Settings and Inputs
10. Back and Home
11. selection kept across a configuration change

Not testable on the emulator, so left for the device (with your approval):
- disabled apps hidden (ZEE5, Sony LIV)
- real HDMI inputs
- physical-remote hold feel
- Kodak frame times

## 7. Screenshots (emulator, API 31 TV, software rendering)

In `reports/still-tv-2-screens/` (`*-small.png` are 1280-px copies):

1. `emu-1-home`: default home
2. `emu-2-focused-favourite`: focused favourite (lift, shadow, name)
3. `emu-3-all-apps`: focus in All Apps (icon-only card with name and light edge)
4. `emu-4-menu`: context menu panel
5. `emu-5-header-focus`: header pill focused
6. `emu-6-inputs`: Inputs panel (the emulator has no HDMI, so the "not available" message shows)

The emulator has only 4 launchable apps, so the dock shows one card there. Kodak screenshots follow the approved preview install.

## 8. Changed files

- **Build/manifest:** `app/build.gradle.kts` (version 2 / 2.0.0, preview switch), `app/src/main/AndroidManifest.xml` (label and HOME placeholders)
- **Kotlin (main):** `MainActivity.kt` (rewritten), `AppRepository.kt`, `FavouriteOrder.kt` (modified); `FocusMap.kt`, `HomeLayout.kt`, `Tiles.kt`, `MenuPanel.kt`, `CalmScrollView.kt` (new); `HoldGesture.kt` (unchanged)
- **Resources:**
  - Rewritten: `layout/activity_main.xml`
  - New layouts: `layout/tile.xml`, `menu_panel.xml`, `menu_row.xml`
  - Changed: `drawable/background.xml`
  - New drawables: `card_shadow.xml`, `card_glow.xml`, `pill.xml`, `panel.xml`, `menu_row.xml`
  - New: `color/on_focus_text.xml`, `values/colors.xml`, `values/ids.xml`
  - Updated: `values/strings.xml`, `values/styles.xml`
  - Removed: `layout/app_tile.xml`, `drawable/tile.xml`, `drawable/control.xml`, `drawable/ic_apps.xml`
- **Tests:** `FocusMapTest.kt`, `HomeLayoutTest.kt` (new), `FavouriteOrderTest.kt` (extended), `SmokeInstrumentation.kt` (rewritten for 2.0)
- **Release artefacts:** `release/1.0-production/` (installed 1.0 APK pulled from the TV, source snapshot, signer record), `release/2.0/` (production and preview APKs)
