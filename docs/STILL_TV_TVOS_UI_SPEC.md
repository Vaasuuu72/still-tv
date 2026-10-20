# Still TV — Apple TV-Inspired UI Engineering Guide

> **Codex implementation brief · Version 1.0 · 19 October 2026**  
> **Project:** `~/projects/still-tv`  
> **Target:** Kodak `PATH_UHDXANDROID`, Android 9 (API 28), Amlogic T962-family, 32-bit ARM, Mali-450 / GLES 2.0, 2 GB RAM, 1920 × 1080 UI  
> **Intent:** Reproduce the visual structure, focus language, and remote-first experience of a contemporary Apple TV 4K / tvOS Home interface **inside the existing native Android launcher**, without copying Apple-proprietary assets or pretending to replace the Android operating system.

---

## Table of contents

1. [How to use this guide](#1-how-to-use-this-guide)
2. [Verified reference and accuracy policy](#2-verified-reference-and-accuracy-policy)
3. [Baseline, scope and non-negotiable constraints](#3-baseline-scope-and-non-negotiable-constraints)
4. [tvOS-to-Still TV screen map](#4-tvos-to-still-tv-screen-map)
5. [Global visual design system](#5-global-visual-design-system)
6. [Detailed UI layout specifications](#6-detailed-ui-layout-specifications)
7. [Interaction and state contracts](#7-interaction-and-state-contracts)
8. [Architecture and performance requirements](#8-architecture-and-performance-requirements)
9. [Verification, screenshots and acceptance matrix](#9-verification-screenshots-and-acceptance-matrix)
10. [Safety, preview builds and rollback](#10-safety-preview-builds-and-rollback)
11. [Master Codex prompt](#11-master-codex-prompt)
12. [Sequential Codex prompts](#12-sequential-codex-prompts)
13. [Delivery checklist](#13-delivery-checklist)
14. [Official references](#14-official-references)

---

## 1. How to use this guide

**Recommended execution:** Save this file in the Still TV repository as `docs/STILL_TV_TVOS_UI_SPEC.md`. Ask Codex to read it in full, run the **Master Codex prompt** for repository reconnaissance, then implement the **Sequential Codex prompts** in order. Each prompt refers back to this document, so there is a single source of truth for design and testing.

**Deliverables expected from Codex:** An actual native Kotlin implementation, screenshot evidence, expanded tests, performance measurements, a preview-only APK, and a separately approved production deployment procedure. **Do not stop at an HTML mockup or a Figma-style description.**

**Priority levels:**

- **P0 — Required:** Home layout, Top Shelf fallback, six-column application grid, icon treatment, focus engine, remote navigation, context menu, app launch/return, safety.
- **P1 — Required before calling the redesign complete:** Edit/reorder mode, folders, quick-controls overlay, native Settings/Inputs access, local app search, accessibility, real-device performance proof.
- **P2 — Optional and only if technically justified:** Launcher-local profiles, lightweight screensaver, a recent-apps *shortcut list* (not a fake OS app switcher), advanced appearance options.

Each section labels the difference between **tvOS observed behavior** and **our Android implementation choice**. Do not turn a design suggestion into a claim that Apple uses the same exact dimensions, code, or animations.

---

## 2. Verified reference and accuracy policy

### Facts supported by Apple documentation

- Apple TV Home lets users reorder apps, use the **top row as favorites / Dock**, and create folders.
- **Top Shelf** is a featured-content area above the top row. On Apple TV, the selected top-row app may provide its own previews; some previews can expand to full screen.
- tvOS app-icon production assets have a **landscape 800 × 480 px** layout and can use **2–5 layers** to create depth when focused.
- The normal Apple TV Home appearance displays **six icons in the top row**; **Large** appearance displays **five**.
- Apple recommends keeping primary tvOS content within a safe area approximately **80 points from the sides and 60 points from the top/bottom**. These are Apple layout points, **not a direct Android dp conversion**.
- D-pad / remote navigation is focus-driven; long-pressing a focused app supports management actions.
- The native Apple TV Control Center contains device functions such as profiles and power; its app switcher shows windows representing open apps.

### What is not established from published references

- Exact Home Screen pixel coordinates, shadow radii, animation spring constants, glass shader algorithms, icon corner curvature, and all versions' current micro-interactions are **not public implementation contracts**. Treat the numerical values later in this guide as **Still TV design proposals** to calibrate against screenshots.
- Android apps do **not** supply Apple Top Shelf data. A native Android launcher cannot promise shows, posters, continue-watching rows, or full-screen trailers from YouTube/JioHotstar/other apps without supported provider APIs.
- A normal Android 9 HOME app does not automatically gain access to OS-level recents thumbnails, global quick settings, system power behavior, streaming-app playback controls, or protected app data.

### Fidelity policy

Target **high visual and behavioral fidelity**, not a deceptive claim that Android is actually tvOS. Use original Still TV branding, installed apps' official package icons/banners, system-provided icons where appropriate, and freely licensed or locally created decorative assets. No Apple wordmark, San Francisco font distribution, Apple TV proprietary icons, stolen tvOS artwork, or copied protected media imagery.

---

## 3. Baseline, scope and non-negotiable constraints

### 3.1 Existing application

- Package ID: `tv.still.launcher`; existing native Kotlin application.
- Current implementation includes `MainActivity`, `AppRepository`, `FavouriteOrder`, and `HoldGesture`.
- Single-Activity / traditional Android Views approach; currently uses a `GridView`.
- Existing icon cache is small; app discovery and favorites are already functional.
- Current launcher is **the default HOME**; Google Android TV Home is still installed but **disabled**.
- ZEE5 and Sony LIV are disabled as previously approved. Do not alter that state without separate approval.

### 3.2 Streaming and device functions that must survive

**Protected apps:**

1. YouTube — `com.google.android.youtube.tv`
2. JioHotstar — `in.startv.hotstar`
3. Crunchyroll — `com.crunchyroll.crunchyroid`
4. F1 TV — `com.formulaone.production`
5. Jellyfin — `org.jellyfin.androidtv`
6. Chromecast built-in — `com.google.android.apps.mediashell` (service, not necessarily a launchable tile)

**Protected system behavior:** Wi-Fi, Bluetooth remote, voice search, HDMI inputs, device Settings, TV sound/picture menus, playback/DRM, sleep/standby behavior, OTA functions, and reliable HOME/Back handling.

### 3.3 Measured Still TV baseline to preserve

| Metric | Reported Still TV 1.x measurement | 2.0 engineering goal |
|---|---:|---|
| PSS / memory | ~17.8 MB | Prefer <= 25 MB; investigate if > 30 MB |
| Idle CPU | ~0% | Remain near zero |
| Navigation CPU | ~3% of one core | Avoid substantial regression |
| Cold launch | ~1.1 s | Near baseline |
| Warm launch | ~0.47 s | Near baseline |
| Navigation p99 | ~25 ms | Match or improve in controlled tests |
| Idle redrawing | Effectively absent | No continuous rendering loop |

These are reported measurements, **not guarantees**. Codex must re-measure on the same Kodak and explain comparable vs. noncomparable tests. Optimize for smooth **perceived navigation** rather than an artificial target FPS.

### 3.4 Hard limits

- No root, bootloader unlock, firmware flash, vendor/system partition edits, new third-party launcher, or modifications to other installed apps.
- No Compose, Flutter, React Native, WebView or heavyweight graphics engine unless a quantified need clearly outweighs the cost (default: **do not add them**).
- No online dependency, recommendation fetch, tracking, analytics, dynamic content carousel, continuously animated background, or unnecessary service/receiver.
- Do not remove, disable, or reset the five protected streaming apps or Chromecast.
- Do not silently update the active HOME APK during design iteration.

---

## 4. tvOS-to-Still TV screen map

| Module | Apple TV reference behavior | Still TV scope | Priority | Key boundary |
|---|---|---|---|---|
| Home grid | Landscape app icons arranged in rows; preferred apps in top row | Recreate closely with one unified ordered app grid | P0 | No separate fake “Favorites” page |
| Top Shelf | Featured content associated with selected top-row app | Static/local app-aware hero/fallback; no copyrighted media feed | P0 | No app content-provider access assumed |
| Focused icon | Elevation, size, layered visual response | Scale, highlight, static depth cues | P0 | No live 3D parallax shader |
| App manager | Long-press app; rearrange and move to folder | Context menu, edit mode, reorder, folder actions | P0/P1 | Maintain keyboard/remote semantics |
| Folders | Group apps; rename, move, dissolve | Native in-launcher folder overlay/scene | P1 | Persist app membership safely |
| Control Center | Floating system quick controls | **Launcher-only** quick controls and supported intents | P1 | No false system-level control |
| Search | Search interface plus integrated Siri/content discovery | Local installed-app search, native voice shortcut | P1 | No universal media index |
| Settings | Lists + detail screens, appearance settings | Still TV preferences plus system Settings | P1 | Do not replace Android Settings |
| App Switcher | OS-level windows for recently opened apps | Optional recently launched app shortcuts | P2 | No cross-app screenshots / forced task switching |
| Profiles | Profile selector and app-specific collections | Optional local launcher layout profiles | P2 | Cannot switch Google/streaming accounts |
| Screen saver | Full-screen motion/photo experience | Optional safe, static/slideshow screen | P2 | Avoid burn-in and battery/CPU cost |
| Playback UI | Native media transport, subtitle and audio interfaces | **Out of scope** | — | Each streaming app owns playback UI |
| Apple TV app | Catalog, Library, Sports, Watch Now and Store | **Out of scope** | — | Separate service, not Home launcher |

---

## 5. Global visual design system

> The values here define the proposed **Still TV** theme on a 1920 × 1080 reference canvas. Calibrate against real Apple TV screenshots, but retain legibility and performance. Convert to density-aware units at runtime rather than embedding pixel constants throughout views.

### 5.1 Art direction

- Mood: quiet, confident, cinematic, understated, premium.
- Hero principle: app icons provide color; the OS chrome stays neutral.
- Avoid faux Apple logos, excessive neon, busy gradients, advertising visuals, unsupported glass shaders, and labeled “feature cards.”
- **Home should visually read as one continuous surface**, not a productivity dashboard.

### 5.2 Proposed tokens (starting points; not claimed Apple constants)

| Token | Initial value | Guidance |
|---|---|---|
| `canvas.background` | `#101115` | Near-black/charcoal, neutral |
| `canvas.backgroundSecondary` | `#1A1C21` | Modal and folder backdrop |
| `text.primary` | `#F4F4F5` | Clear legibility at distance |
| `text.secondary` | `#B5B7BF` | Secondary labels and hints |
| `focus.stroke` | soft white, moderate opacity | Maintain focus in bright app tiles |
| `focus.surfaceGlow` | subtle precomputed overlay | Do **not** use `RenderEffect`/runtime blur (API 28) |
| `surface.panel` | opaque / lightly translucent graphite | Avoid full-screen blur |
| `surface.scrim` | dark alpha overlay | For context menus/folders |
| `spacing.base` | 8-unit scale | Consistent vertical rhythm |
| `tile.aspect` | `5:3` / approx. `1.667` | Matches landscape 800:480 art ratio |
| `tile.radius` | Start around 12–18 Android dp | Tune visually on TV |
| `tile.focusScale` | Start `1.055` | Test `1.04–1.07`; avoid clipping |
| `motion.focus` | Start `130–170 ms` | Property animations only |
| `motion.modal` | Start `100–150 ms` | Short fade/translation; no blur |

Support accessibility **Reduce motion**: immediate focus update or shortened animations. Include a **Large icons** appearance option that shows five icons in the first row.

### 5.3 Proposed 1080p composition

A reference **six-column** layout, pending measured Android density conversion:

- Screen: 1920 × 1080 physical UI pixels.
- Primary overscan-safe inset: start around **90–100 px horizontally** and **65–75 px vertically** on the design canvas; convert/adapt to actual view coordinates and verify physical overscan.
- Six-column area target: **approximately 1728 px content width**.
- Candidate six-column tile: **258 × 155 px** at 5:3, with **36 px** horizontal spacing (`6 × 258 + 5 × 36 = 1728`). These are *composition guides*, not fixed Android XML pixels.
- Leave additional visual clearance for the focused tile's scaling beyond its normal bounds.
- Large-icon mode: five columns, computed dynamically from usable width and spacing.
- Top Shelf occupies a visually substantial region above row one. It may be tall or compact depending on top-row focus, but must not jump or force a full grid rebind on every D-pad press.
- Lower rows scroll under the header/hero region in a predictable way; the current selection stays visible.
- Use proportional or resource-based layout sizing so smaller/larger displays remain usable.

### 5.4 Typography

- Use a system-available modern sans font with high x-height and good TV readability.
- No bundled Apple San Francisco font files.
- Clear hierarchy: hero/app name > menu headings > tile labels > helper text.
- Prefer medium weights and 1-line app names; ellipsize long names gracefully.
- Don't repeat app-name labels unnecessarily inside icon art *and* beneath every tile if it makes the grid crowded. Test both treatments against references.
- Text must remain readable at normal sofa viewing distance, not only in emulator screenshots.

### 5.5 Icon asset policy

1. Prefer officially provided `LEANBACK_LAUNCHER` banners / TV banners where available.
2. If no banner exists, use the installed app's official launcher icon inside a uniform landscape tile with neutral background and good padding.
3. Preserve logos and original aspect ratio: **never stretch a square logo into a landscape rectangle**.
4. The Apple **800 × 480** number describes a tvOS design asset, not a command to decode every Android icon at 800 × 480. Decode at on-screen size appropriate to display density.
5. Cache scaled results with strict memory cap and lifecycle-safe invalidation; update for app install/uninstall/change.
6. Avoid asynchronous focus artifacts where one tile momentarily displays a different app's icon.

### 5.6 Motion and focus

- Every visible interactive item has clear focus, not only the app grid.
- Focus transition uses cheap `scaleX`, `scaleY`, `translationY`, `alpha` and lightweight foreground/border changes.
- Never rerender app icons just because focus moved.
- Use a single logical focus owner. Restore focus by **stable app/package or folder ID**, not fragile grid index alone.
- Prevent z-order clipping, neighbor overlap, accidental scrolling, focus traps, and hidden selection.

---

## 6. Detailed UI layout specifications

### UI-01 — Home: top row and full app grid **[P0]**

**Apple reference:** One Home Screen, with the top row being part of the app grid rather than a separate favorites module. The top row determines the Top Shelf preview. Users arrange items directly; normal appearance shows six icons, Large appearance five.

**Still TV implementation:**

- Replace the existing “Favourites” + “All apps” split visual structure, if present, with **one consistent ordered home grid**.
- First six positions constitute the top/Dock row (five in Large mode). The first-run sequence should prioritize installed **YouTube, JioHotstar, Crunchyroll, F1 TV, Jellyfin**, then other eligible apps deterministically.
- **Important:** Existing saved custom orders and pinned choices take precedence. Do not overwrite existing user data with new defaults.
- Keep a minimalist upper visual region and calm background. No permanent large “STILL TV / YOUR APPS” headings if they disrupt visual fidelity. Use subtle branding only where it does not compete with the icons.
- Show enough of the next app row so the screen is visibly scrollable.
- Scroll in discrete predictable increments, preserving focused item's screen position where possible.
- On HOME key (when routed by Android), render quickly; restore last meaningful focus unless user intentionally reset.
- Display only installed **enabled launchable apps**. Chromecast is a service and must remain functional without being forced into the grid.

**Acceptance:** six landscape tiles aligned across; top row behaves as same grid as all other rows; one selection; no blank cells from disabled ZEE5/Sony LIV; all protected apps present and launchable.

### UI-02 — Top Shelf / upper hero **[P0]**

**Apple reference:** A Top Shelf area above the top row may show featured previews linked to the currently focused top-row app. Apps can provide rich content on real tvOS.

**Still TV limitation:** Android installed apps do not give us Apple Top Shelf content. We must **not invent continue-watching items, scrape streaming services, or ship fake media art.**

**Proposed implementation:**

- Create an app-aware **lightweight Top Shelf fallback** that activates only when focus is in top row.
- Show large but restrained app identity: app name/logo and optional flat backdrop derived from the installed app's icon/banner and color palette.
- Default fallback is static. No timers, network requests, infinite scroll, auto-play, rotating posters, or background continuous draw.
- On focus change, update *only the hero metadata and the minimum necessary views*.
- Consider a subtle static crossfade only if measured render performance allows it. Disable effects when Reduce Motion is enabled.
- When focus moves to lower rows, compact or hide hero without a jarring full-screen rearrangement.
- The Up action from the top row must not jump unexpectedly to tile one. If a focusable hero action exists, Up can move to it; otherwise Up is a no-op and focus stays on the app.

**Acceptance:** no fake streaming content, idle CPU near zero, hero never redecodes huge images on every D-pad press, no focus loss.

### UI-03 — App tile default, focused and pressed states **[P0]**

**Apple reference:** Rounded landscape icons; focused icon increases apparent depth, light and scale. tvOS layered production icons support subtle parallax on Siri Remote.

**Still TV:**

- `Default`: clear banner/logo, balanced padding, gentle round mask, no permanent neon outline.
- `Focused`: ~1.055 scale, tiny upward translation, soft neutral highlight/border, optional shallow non-blurred shadow. Use efficient hardware-layer/property animation only if it measurably improves frame timing; release it when animation finishes.
- `Pressed`: brief tactile visual response without delaying launch.
- `Disabled / unavailable`: do not show disabled apps as though launchable; refresh dataset and recover focus if uninstall occurs.
- `Focused + long-press`: trigger existing 250 ms HoldGesture once, then cancel ordinary click event to avoid accidentally opening the app.
- Keep label placement consistent; on selected item it may become more prominent, but must not push nearby items.
- Respect TalkBack/accessibility focus and `contentDescription` including action hints.

**Acceptance:** no clipping at screen edges; no adjacent tile occlusion; visual focus visible on both bright and dark logos; no repeated image decode.

### UI-04 — Long-press context menu **[P0]**

**Apple reference:** Holding an app provides management actions such as rearrangement and folder operations.

**Still TV:**

- Compact floating menu anchored visually to selected tile but repositioned within safe bounds.
- Dim background mildly; preserve clear selected-app context.
- Actions: **Edit Home Screen**, **Move to Folder** / **New Folder** (after folder feature implemented), **App info** (safe system intent), **Cancel**; optional pin shortcut only if compatible with unified grid model.
- No fake “Delete system app” action. If uninstall is supported by Android UI, explicitly label it and show standard system confirmation.
- D-pad Up/Down cycles menu, OK activates, Back cancels and restores original tile focus.
- Menu must be fast, non-modal-to-system, and free of heavy blur.
- Preserve current 250 ms hold threshold initially; test inadvertent opens with actual remote and adjust only with evidence.

**Acceptance:** one menu per hold, single correct action, zero click-through, focus returns exactly to original app.

### UI-05 — Edit Home Screen / drag-and-reorder **[P1]**

**Apple reference:** Long-press -> Edit Home Screen -> move item -> select to save.

**Still TV:**

- Present a clear edit state using modest tile lift or outline; subtle wobble only if low-cost and motion allowed; static edit state is fine.
- Reorder via D-pad while an item is picked up; show a visible insertion/drop target and allow Save, Cancel and Back behavior.
- Support moving items between top row and lower rows.
- Persist order atomically; retain unknown/uninstalled app references in a migration-safe way or prune predictably after confirmation.
- Avoid rewriting preference storage on every key press; commit on save.
- If launchable app list changes while editing, safely reconcile and avoid a corrupted order.
- Explicitly test edit cancel restores prior order, and reboot preserves saved order.

**Acceptance:** no disappearing apps, duplicates, or lost focus; safe even after rapid navigation.

### UI-06 — Folders **[P1]**

**Apple reference:** Apps can be placed in named folders, renamed, moved out; folder disappears when emptied.

**Still TV:**

- Folder is a grid item with a clear composite preview made from child icons, precomputed at small size.
- Opening a folder shows a centered foreground panel, a concise title, grid of contained apps, and darkened background home view.
- Folder panel adapts to remote control and display safe area; focused item remains clear.
- Commands: Create Folder, Move to Existing Folder, Rename Folder (via Android TV IME), Move to Home, Reorder Within Folder.
- Give folders stable IDs, unique names not required, store ordered package membership, and provide deterministic migration.
- When last app leaves, automatically remove empty folder or request confirmation if needed; no orphan state.
- On Back return focus to folder tile; on app return restore within-folder state where appropriate.

**Acceptance:** create/rename/move/dissolve flow works, persists through restart, handles removed/disabled packages, remains accessible with the remote.

### UI-07 — Launcher quick controls / Control Center-inspired overlay **[P1]**

**Apple reference:** Global Control Center presents quick functions in floating grouped panels and may be opened by holding the TV remote button.

**Still TV reality:** We cannot assume our Android launcher intercepts all system button events or can change system-only settings directly.

**Implementation:**

- Expose an **on-home-screen quick-controls button or configurable non-conflicting remote shortcut**. Do not hijack Home, voice mic, volume or power keys globally.
- Floating right-side or upper-right panel with short grouped entries: Settings, Inputs, Apps/Search, optional launcher Appearance; display date/time if lightweight.
- Only show a direct toggle for a function when a supported public API exists and permission is legitimate. Otherwise use the corresponding native Android Settings screen.
- Do not fabricate Wi-Fi/Bluetooth status controls, AirPlay, Apple Account profiles, HomeKit, audio-route controls, or a working power-off command.
- If genuine standby can be invoked safely via documented system behavior, gate it with device testing; otherwise omit Power.
- Back closes overlay and returns focus to trigger control/tile.

**Acceptance:** all visible controls work; no permissions creep; no conflict with remote Home/voice/volume.

### UI-08 — Search **[P1]**

**Apple reference:** Search provides text/voice discovery and may aggregate media across integrated services.

**Still TV:**

- Provide local **installed-app name search**, not global movies/shows search.
- Show a clean search panel with Android's existing IME, live matching app tiles, clear empty/no-result state.
- Keyboard and remote navigation are both supported.
- Input focus and Back behavior are predictable: close keyboard, then dismiss search (where appropriate).
- Maintain offline-only operation; no user data indexing or app content introspection.
- Preserve existing voice-search functionality by delegating to the supported Android voice intent when available; no fake Siri interface.

**Acceptance:** case-insensitive app matching, keyboard works, Back works, no sensitive permission, found app launches.

### UI-09 — Still TV Settings + native TV Settings **[P1]**

**Apple reference:** Settings is a structured list/detail interface, with appearance, accessibility and screen saver sections.

**Still TV:**

- Have two clearly separated choices: **Still TV Preferences** and **Android TV Settings**.
- Still TV Preferences should use a tvOS-inspired list/detail layout, not a phone-settings sheet.
- Preferences candidate fields: Appearance (Dark / optional Light), Icon Size (Default 6 / Large 5), Reduce Motion, Top Shelf fallback On/Off, time display, reorder/reset Home layout with confirmation, About/version.
- System functions remain in Android TV Settings; preserve manufacturer TV picture/audio/input flows.
- Avoid changing Android-wide accessibility/animation settings without permission.
- Preference updates must be immediate, persistent, and reversible; do not recreate/rebind the entire launcher on every minor preference toggle unless necessary.

**Acceptance:** no trapping in submenus, no blank screen after returning from Android Settings, keyboard/remote flows correct.

### UI-10 — Profiles **[P2, optional]**

**Apple reference:** Control Center can switch between tvOS profiles that alter certain Apple services' recommendations and collections.

**Still TV possibility:**

- Launcher-local profiles can maintain independent app orders, folders and appearance preferences only.
- They **do not log users into or out of YouTube, Hotstar, Jellyfin, Google Play or Android OS accounts**.
- Only implement if multiple-user needs are confirmed. Otherwise remove the icon entirely rather than pretending it works.
- Use simple local storage, explicit switch UI, and no network access.

**Acceptance:** profile switching changes launcher-local layout only; no accidental account sign-outs.

### UI-11 — Recently used apps / App Switcher-inspired screen **[P2, optional]**

**Apple reference:** OS App Switcher shows live/recent app windows and supports switching / force quitting.

**Still TV boundary:** Normal Android 9 launcher access to arbitrary running-task thumbnails and reliable cross-app task switching is restricted. **Do not reproduce fake screenshots, claim true OS recents, force-stop apps or request invasive permissions.**

**Allowed fallback:** A small “Recently launched” list maintained by Still TV itself, using package names and app icons from launches initiated within the launcher. Relaunching an app is not equivalent to resuming a task.

**Acceptance:** accurately labeled as shortcuts; no background app monitoring; no misleading close buttons.

### UI-12 — Screensaver **[P2, optional]**

**Apple reference:** Full-screen Aerial/photographic/animated screensavers with limited controls.

**Still TV:**

- Investigate built-in Android Daydream/screensaver support before writing custom code.
- If a custom screensaver is warranted, prefer static local imagery or infrequent transitions and clear navigation/exit; no video stream or remote asset downloads.
- Do not interfere with TV standby, sleep timer, burn-in precautions, or existing manufacturer screensaver settings.
- Explicitly test that launcher does not maintain an always-on render loop when idle.

**Acceptance:** minimal resource use and clean wake/exit; otherwise leave native screensaver alone.

### UI-13 — Inputs and HDMI selection **[P0 preserve; P1 visual polish]**

- Preserve the **already working** HDMI 1–3 / AV 1–2 input listing in this device's deployment and verify supported sources again.
- Use existing `TvInputManager` and documented routing. No `droidlogic` vendor code changes.
- Render inputs in same rounded focusable row/menu treatment as the rest of Still TV.
- Label unavailable sources honestly and avoid sending unsupported intents.
- Return to Still TV without losing focus where the system allows.

### UI-14 — App launch / Back / Home / recovery **[P0]**

- Do not touch playback UI owned by YouTube, Hotstar, Crunchyroll, F1 TV or Jellyfin.
- Launch selected app by its resolved supported TV/main activity using existing safe repository mechanism.
- If launch fails, show concise non-blocking feedback and remain in launcher.
- On coming back from app, restore the correct focused tile and scroll offset.
- Ensure Home returns to Still TV even when the selected app is gone or disabled.
- Long Back must not produce a blank screen or exit HOME unexpectedly.
- A crash should recover via Android HOME behavior or a verified fallback; **never disable both current and fallback HOME**.

---

## 7. Interaction and state contracts

### 7.1 D-pad / key matrix

| Screen/state | Left / Right | Up / Down | OK / Enter | Hold OK (~250 ms) | Back |
|---|---|---|---|---|---|
| Home grid | Adjacent item or edge no-op | Next/previous row; Top Shelf only if an actual focusable target exists | Launch app / open folder | Open tile context menu | Stay at HOME / defined screensaver shortcut only if tested |
| Context menu | No-op or submenu | Select action | Activate action | No repeat activation | Close and restore tile focus |
| Edit grid | Move picked tile/drop target | Move picked tile/drop target | Pick/place/finish as designed | No accidental app launch | Cancel current drag; then exit edit |
| Folder | Move within folder | Move within folder | Launch/open item | Context menu on child | Close folder and restore folder focus |
| Quick controls | Change tab/group if present | Select control | Execute supported action | No special action | Close overlay |
| Search | Navigate results/keyboard | Navigate results/keyboard | Launch result / input | No special action | Keyboard dismiss, then search close |
| Preferences | Adjust selectable value if appropriate | Navigate setting rows | Open/change | No special action | Previous settings panel or HOME |

**Events:** handle Android key down/up/repeat correctly. Prevent duplicate click after a hold event. Test ADB key events and actual physical remote. The real remote is the final authority.

### 7.2 State rules

- Persist stable **package/component identifiers**, not only grid indices.
- Keep per-screen focus return targets (Home, folder, menu, Settings).
- Maintain single source of truth for ordered top-level items (apps and folders) and folder membership.
- Migrate existing `FavouriteOrder` preferences without data loss. Take a backup of old settings before migration.
- If an app is uninstalled/disabled, update representation and restore focus to nearest valid item.
- No cyclic focus traps; every visible actionable control reachable by remote.
- Do not send Android key events to other applications or intercept unrelated system controls.

### 7.3 Accessibility

- Readable at normal TV distance; test sofa distance and overscan.
- Focus contrast works on all brand backgrounds.
- Use meaningful content descriptions, accessibility focus, and announcement for state changes.
- Large icons mode; reduce motion; avoid rapid flashing and continuous animations.
- Text handles long names, localization and right-to-left layouts where feasible.

---

## 8. Architecture and performance requirements

### 8.1 Preferred component boundaries

Codex must first inspect the actual repository; class names below describe **responsibilities**, not mandatory filenames.

| Component | Responsibility | Constraints |
|---|---|---|
| `HomeScene` / Activity | Layout, lifecycle and focus integration | No network/UI polling |
| `AppRepository` | Package discovery, icons and launch intents | Reuse existing cache, lifecycle safe |
| `HomeOrderRepository` | Top-level order, first-run defaults, migration | Stable IDs and atomic persistence |
| `FolderRepository` | Folder membership/names/order | Handle disabled/uninstalled apps |
| `FocusCoordinator` | Cross-row focus and restore | No top-row Up jumps |
| `TopShelfRenderer` | Static hero state from focused top-row app | No dynamic content fetching |
| `ContextMenuController` | Long-press actions, cancel, focus return | No click-through |
| `QuickControlsController` | Supported Settings/Inputs links | No fake toggles |
| `AppearanceSettings` | Icon size, theme, reduce motion | Lightweight preference updates |

Keep architecture proportionate: this is a small native launcher, not a large architecture showcase. Prefer small testable classes over excessive abstraction.

### 8.2 Rendering and allocation policy

- Traditional Android Views and TV-focused layout utilities; `RecyclerView` may be appropriate when a full grid/folder system outgrows `GridView`, but justify migration.
- Do not add runtime blur/refraction, GLSL custom effects, continuous spring simulations, or per-frame icon recomposition.
- Precompute masks/tinted backgrounds and cache images at rendered size.
- Load per-app icons lazily; guard stale async results by package ID.
- Avoid permanent view invalidation and clock update every second; minute-granularity time is enough if displayed.
- Do not decode multi-megabyte preview artworks into native heap.
- Run heavier package discovery outside the UI thread and apply a small, stable diff to the visible data.
- Treat launcher focus changes as **no I/O** events in the common path.

### 8.3 Performance methodology

At a minimum, preserve raw measurements for:

1. Cold launch after force-stop *in controlled testing only* (do not force-stop the only active HOME casually).
2. Warm return from another app.
3. 60-key D-pad benchmark and real remote subjective check.
4. 30-second idle HOME with top-row item focused.
5. Same idle test with lower row focused.
6. Open/close context menu 20 times.
7. Folder enter/exit, Settings round-trip, HDMI input round-trip.
8. Ten protected-app launch/return cycles.
9. Long-term idle and memory stability (as feasible without unnecessary device modifications).

Collect `dumpsys gfxinfo`, PSS, CPU, logcat ANRs/crashes and frame percentiles. Reset **diagnostic counters only** if required, and document the benchmark conditions. Report medians and tail latencies; distinguish banner-related stock launcher results from direct Still TV comparisons.

---

## 9. Verification, screenshots and acceptance matrix

### 9.1 Required screenshot set

Use the actual Kodak device for final visual review, preferably via ADB screenshot capture after explicit preview-install approval:

- `01-home-default.png` — six-column grid, top row and hero
- `02-home-focus-bright.png` — focused light/bright application
- `03-home-focus-dark.png` — focused dark application
- `04-home-scrolled.png` — lower grid rows and focus preservation
- `05-context-menu.png`
- `06-edit-mode.png`
- `07-folder-open.png`
- `08-folder-rename.png`
- `09-quick-controls.png`
- `10-app-search.png`
- `11-still-tv-settings.png`
- `12-inputs.png`
- `13-large-icons.png`
- `14-return-from-app.png` — focus after coming back from one protected app

If a module is intentionally deferred P2, mark it **not implemented** instead of supplying a deceptive mockup.

### 9.2 Visual acceptance

- [ ] Visually coherent unified Home grid, not separate favorite/list dashboards
- [ ] Landscape 5:3 artwork treatment without distorted brand logos
- [ ] Six columns default / five columns Large, with no clipping
- [ ] Top Shelf occupies appropriate area without an always-on animation
- [ ] Dark neutral system chrome; color comes primarily from apps
- [ ] Focused tile has immediate, obvious but restrained scale/elevation
- [ ] Correct spacing, alignment, typography and TV-safe margins
- [ ] Menus, folders, search and preferences share one design language
- [ ] No fake streaming previews, fake OS toggles or copied Apple UI artwork

### 9.3 Functional acceptance

- [ ] Protected apps launch and return correctly
- [ ] Chromecast service unchanged, TV voice search still works
- [ ] HOME always resolves to production Still TV during preview testing
- [ ] D-pad focus navigation is deterministic and complete
- [ ] Up on top row no longer jumps to first tile
- [ ] Hold-OK action does not launch app as side effect
- [ ] Order/folders persist through app restart and reboot
- [ ] Disabled ZEE5/Sony LIV do not appear as active app tiles
- [ ] Settings, inputs and Back continue working
- [ ] No crashes/ANRs in repeated cycles

### 9.4 Performance acceptance

- [ ] Idle CPU remains close to zero while focused on top row
- [ ] No high-frequency idle redraw or garbage-collection loop
- [ ] PSS preferably <= 25 MB; any > 30 MB must be investigated
- [ ] Startup remains close to previous 1.1s cold / 0.47s warm
- [ ] Navigation p99 not materially worse than previous ~25 ms
- [ ] Performance changes reported with raw runs and uncertainties

---

## 10. Safety, preview builds and rollback

**Critical device state:** Still TV is currently HOME, and stock `com.google.android.tvlauncher` is installed but disabled. That means a bad update to production Still TV risks leaving no usable HOME. Design/test work must therefore use a safe preview path.

### Preview build contract

1. Capture version, active HOME, disabled packages, current app signing identity and recoverable APK backup availability.
2. Confirm known-good ADB at `192.168.1.8:5555`, which was previously verified to reconnect after reboot. Reverify before deployment.
3. Build preview with **different `applicationId`**, e.g. `tv.still.launcher.preview`; remove/disable HOME intent declaration from the preview manifest so it cannot compete for default HOME.
4. Confirm the preview launches explicitly by a safe intent/activity and can return to the current production launcher. Do not migrate/overwrite production data from preview.
5. **Request approval before installing anything on the physical television.**
6. Functional and screenshot acceptance on the preview build **before** any production update.
7. Save installed production APK if feasible via authorized `pm path` and `adb pull`, verify signing compatibility / restore path, and keep the stock launcher recovery commands available. Do not assume data survives uninstall/reinstall.
8. Update production only when the same-signature APK upgrade and recovery mechanism are established and separately approved.
9. If production HOME cannot launch, use proven ADB recovery to **re-enable stock HOME**: `adb shell pm enable com.google.android.tvlauncher` (and resolve preferred HOME if necessary). Validate before relying on any other fallback.
10. Never disable both launchers at once. Never uninstall production Still TV as a shortcut to test the preview.

### Recovery commands (illustrative; verify on device)

```bash
adb connect 192.168.1.8:5555
adb devices -l
adb shell pm enable com.google.android.tvlauncher
adb shell pm list packages -d
```

**Note:** The known firmware previously preferred stock Android TV Home over Still TV unless stock HOME was disabled. Therefore a normal Android HOME picker / `set-home-activity` command is **not guaranteed** to produce the desired state. A recovery drill must check the actual resolved HOME. Rooting is **not** part of this project.

---

## 11. Master Codex prompt

> Paste this once to initiate the project. Codex should use Sections 1–10 as the binding product specification and Sections 12–13 as the execution plan.

```text
You are the principal Android TV UI engineer for Still TV, our already deployed
custom launcher on a Kodak PATH_UHDXANDROID TV.

Read docs/STILL_TV_TVOS_UI_SPEC.md in full. Treat its scope, screen specifications,
protected-app list, performance targets, acceptance matrix, and deployment safety
rules as the definitive requirements.

Objective: make Still TV visually and interactively faithful to the contemporary
Apple TV 4K/tvOS Home experience: a unified six-column landscape-icon app grid,
app-aware upper Top Shelf region, elevated focus states, native-feeling edit/reorder,
folders, concise floating controls, search and preferences.

Preserve original Still TV branding and official installed-app icon assets. Do not
copy Apple-proprietary assets or claim to implement system features unavailable to
an Android 9 HOME application.

Current production Still TV is the only actively enabled HOME. Never overwrite it
before preview-build tests and explicit approval. Existing protected streaming
apps, Chromecast, voice search, HDMI and device settings must keep working.

Start with a repository audit and an implementation map. Then implement modules
in the prescribed phases, running tests and recording screenshots/benchmarks.
Do not ask for permission for local code edits/build/tests, but ask before any
install, system modification or replacement on the physical TV.

First response: list the real source files you found; describe present visual
layout and migration risks; identify the minimal sequence of changes; and begin
Phase A local implementation. Do not fabricate files, screenshots or results.
```

---

## 12. Sequential Codex prompts

These prompts are **individually runnable**. Use them in order after reading the complete specification. **Never run a later deployment prompt as authorization for an earlier physical-device action.**

### Prompt A — Architecture audit, design tokens and source mapping **[P0]**

```text
Read docs/STILL_TV_TVOS_UI_SPEC.md Sections 1–10 and inspect the entire current
Still TV repository (especially MainActivity, AppRepository, FavouriteOrder,
HoldGesture, manifest, resources, Gradle and existing tests).

Map each UI-01..UI-14 requirement to current components and identify the
smallest safe architecture update. Examine how HOME is declared and how the
existing default installation can be recovered. Propose stable ordered-grid
and folder persistence models with non-destructive migration from favorites.

Implement common design tokens, dimensions, colors, reusable focus styles and
any lightweight infrastructure required for Phase B. Run tests and lint.

Output: source-file map, migration plan, design tokens, risks, changed files,
and test evidence. Do not deploy or change default HOME.
```

### Prompt B — Unified Home grid and Top Shelf fallback **[P0]**

```text
Implement UI-01 and UI-02 from docs/STILL_TV_TVOS_UI_SPEC.md.

Create one ordered 6-column landscape app grid, with the top row as the Dock
(5 in Large mode). Preserve previously saved user order; on true first run,
prioritize installed YouTube, JioHotstar, Crunchyroll, F1 TV and Jellyfin.
Use real installed app banners/icons, never stretch logos.

Implement a lightweight, static app-aware Top Shelf region for top-row focus.
No fake show posters, streaming scraping, network requests, rotating banners
or continuous idle animation. Fix Up-from-top-row jumping to tile one.

Test enabled/disabled app discovery, top row, scrolling, focus restoration,
empty states, long app names, 1080p geometry and Reduce Motion. Produce emulator
screenshots and validate that it is still a native, offline Android TV launcher.
Do not install on the Kodak yet.
```

### Prompt C — Icon fidelity, focus and motion **[P0]**

```text
Implement UI-03 and shared design system from the guide.

Introduce crisp rounded landscape tiles and a consistent default/focused/
pressed state. Proposed focused scale ~1.055 with inexpensive transform and
subtle neutral outline/elevation. Ensure focus remains visible on very bright
and dark logos; prevent clipping and neighbor overlap. Use correct icon/banners
from installed packages with efficient cache and no repeated decoding on focus.

Make all D-pad transitions deterministic at row boundaries and after a quick
sequence of presses. Include a Reduce Motion fallback. Run unit/UI tests,
record focus screenshots and measure idle invalidation in emulator.
Do not introduce blur shaders, video or heavy dependencies.
```

### Prompt D — Long-press menu, edit mode and folders **[P0/P1]**

```text
Implement UI-04, UI-05 and UI-06.

Preserve the existing 250 ms HoldGesture unless physical-device evidence shows
misfires. Build a compact context menu with actions to edit order, create/move
to folders, open system App info and cancel. Ensure hold does not also click.

Create an edit state with D-pad reorder and explicit Save/Cancel. Support moving
items between Dock and subsequent rows without duplicating or dropping apps.
Create persistent named folders with predictable open/close focus, rename via
TV keyboard, moving apps in/out and auto-dissolving empty folders. Migrate old
favorite orders without overwriting them.

Add focused tests for rapid navigation, cancel, persistence/restart, disabled
apps, uninstall/change events, folder naming and focus restoration.
Do not deploy yet.
```

### Prompt E — Quick controls, search, Settings and inputs **[P1]**

```text
Implement UI-07, UI-08, UI-09 and UI-13 as launcher-local surfaces.

Create a compact Control Center-inspired panel reachable by a visible launcher
control or a verified non-conflicting shortcut; do not hijack global keys.
Expose real supported actions: Android TV Settings, available HDMI/AV inputs,
local app search, and Still TV appearance settings. Do not fabricate system
Wi-Fi/Bluetooth/Power/volume toggles. Preserve Chromecast and Android voice search.

Implement offline installed-app search with TV IME and reliable Back behavior.
Implement list/detail Still TV settings for Dark/optional Light, default/large
icons, Reduce Motion and hero enable/disable. Preserve native TV Settings,
manufacturer picture/audio menus and already working input switching.

Run functional tests and capture emulator screenshots; prepare TV-specific
verification steps. Do not install on the physical device without approval.
```

### Prompt F — Optional feature decision **[P2]**

```text
Review UI-10 (launcher profiles), UI-11 (recently launched shortcuts), and
UI-12 (screensaver). For each, provide actual user value, API feasibility on
Android 9, implementation cost, memory/CPU budget and limitations.

Do NOT implement a fake Apple global profile switcher, true OS task-switcher,
AirPlay panel or streaming playback controls. Do NOT request invasive usage
or accessibility permissions to imitate OS functionality.

Implement only explicitly approved features with clear launcher-only labels.
Otherwise mark them deferred and keep the Home interface uncluttered.
```

### Prompt G — Full QA, device preview and performance **[P0/P1]**

```text
Perform the acceptance matrix from Sections 9 and 10.

Build a separate preview APK with a different application ID and NO HOME intent.
Confirm the active Still TV package stays unchanged. Run full tests, lint,
preview screenshots, icon quality review and automated remote navigation.

Before deploying to Kodak, show the APK path, diff summary, preview manifest
and recovery plan and request my approval. Only after approval, install preview
on the physical TV and execute on-device screenshot, key navigation, protected
streaming apps, voice, Chromecast, Settings, HDMI, and repeated return tests.

Benchmark 3 matched runs for memory, cold/warm startup, navigation frame-time
percentiles, idle CPU/frames, GC and menu/folder actions. Compare honestly with
previous Still TV baseline. Clearly label missing physical tests.

Output: reports/still-tv-2-design-review.md,
reports/still-tv-2-performance.md,
reports/still-tv-2-acceptance.md,
and reports/still-tv-2-release-readiness.md.
Stop before modifying production HOME.
```

### Prompt H — Production release preparation only **[P0, separate approval]**

```text
Prepare (do not execute) a production update plan for tv.still.launcher.

Confirm code-signing continuity, safe APK backup, preference schema migration,
ADB recovery after reboot, and that Android TV Home can be re-enabled. The
production launcher is currently HOME and the stock launcher is disabled.

Provide exact, tested preflight steps, the proposed production update commands,
verification steps, rollback commands and failure-stop conditions. Do not
uninstall production Still TV or disable both HOME candidates.

Ask me for explicit authorization before any production installation. After
an authorized release, verify physical Home button, app launch/return, a reboot,
restored saved app order/folders and streaming/voice/inputs/Chromecast.
```

---

## 13. Delivery checklist

Codex should produce all applicable items:

```text
still-tv/
├── app/                              # Native Kotlin launcher implementation
├── docs/
│   └── STILL_TV_TVOS_UI_SPEC.md      # This guide, retained as design contract
├── reports/
│   ├── still-tv-2-design-review.md
│   ├── still-tv-2-performance.md
│   ├── still-tv-2-acceptance.md
│   ├── still-tv-2-release-readiness.md
│   └── screenshots/                 # Real screenshot evidence; no fabricated output
└── scripts/                          # Reviewed preview/install/rollback utilities
```

**Completion gate:** The visual review passes, the five protected streaming apps and Chromecast work, remote and settings/inputs are stable, no continuous idle work appears, real TV benchmark results are documented, and production update/rollback are explicitly approved **separately**.

**Known limitations to report rather than hide:** genuine Apple Top Shelf feeds, Apple's own Control Center, Siri, tvOS profiles, OS app-switcher snapshots, AirPlay, Apple media library, and playback UIs inside external apps cannot be assumed available on Kodak Android 9.

---

## 14. Official references

These sources support the Apple-specific design facts and affordances above. Links are for research/visual calibration. Implementation details, numbers under Section 5 and Android fallback designs are proposed **Still TV engineering decisions**, not Apple specifications.

1. **Apple — tvOS App Icons (shape, 800 × 480px, layered focus):** https://developer.apple.com/design/human-interface-guidelines/app-icons
2. **Apple — Top Shelf:** https://developer.apple.com/design/human-interface-guidelines/top-shelf
3. **Apple — Layout, safe area and tvOS grids:** https://developer.apple.com/design/human-interface-guidelines/layout
4. **Apple — Focus and selection:** https://developer.apple.com/design/human-interface-guidelines/focus-and-selection
5. **Apple — Rearrange apps and create folders:** https://support.apple.com/en-in/guide/tv/atvbad14dc6a/tvos
6. **Apple — Navigate Apple TV 4K:** https://support.apple.com/en-in/guide/tv/atvbe55a4b4e/tvos
7. **Apple — Appearance (six regular / five large icons):** https://support.apple.com/en-in/guide/tv/atvb46af4f11/27/tvos/27
8. **Apple — Control Centre:** https://support.apple.com/en-in/guide/tv/atvb0a416871/tvos
9. **Apple — Switch between apps:** https://support.apple.com/guide/tv/switch-between-apps-atvbbcec4f4e/26/tvos/26
10. **Apple — Search:** https://support.apple.com/en-gb/guide/tv/atvb8f84a271/tvos
11. **Apple — Screensavers:** https://support.apple.com/guide/tv/activate-or-change-the-screen-saver-atvb6bd6a97d/tvos
12. **Apple — Reduce Motion:** https://support.apple.com/en-in/guide/tv/atvb1f949820/27/tvos/27

---

## Plain-language summary

This guide asks Codex to turn **the existing, fast Still TV** into a polished Apple TV-inspired interface *screen by screen*. It preserves the real Kodak Android foundation and your streaming apps, replaces the simple grid with a unified tvOS-style Home, adds refined focus, optional Top Shelf fallback, app management, folders, quick controls, search and appearance settings, and then proves on-device responsiveness before any production update. It **does not** root the TV, claim to run tvOS, copy Apple-proprietary assets or replace external apps' playback screens.
