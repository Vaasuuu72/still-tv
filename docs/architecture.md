# Architecture

## Design choices

Still TV uses one native Kotlin Activity, XML layouts, and Android’s built-in GridView/BaseAdapter recycling. GridView avoids adding RecyclerView and its dependency tree while retaining bounded visible view allocation and native D-pad/long-press behavior. This is a conservative starting point for unknown TV hardware, not a conclusion that the Kodak is low powered. Revisit only when measured performance justifies it.

The display uses a static near-black gradient, plain type, 128 dp tiles, and a 3.5% focus scale over 110 ms. Only scale is animated; no blur, video, animated wallpaper, shadows or continuous renderer. Focus gets an immediate high-contrast outline. System animator scale zero removes movement. The grid uses 2–6 columns according to available dp width, with overscan-safe outer padding and scrolling. Text is single-line with ellipsis; full app names remain available to accessibility and management dialogs.

## Components

`MainActivity`: lifecycle, grid/footers, page/selection restoration, remote events, contextual management, Settings/input intents and error feedback. It preserves the selected package across refresh, app return and rotation; home favourites selection also survives process death. HOME intents return to favourites. The user selects the default HOME outside the app.

`AppRepository`: PackageManager discovery, ordered local SharedPreferences/JSON, and a 2 MiB bitmap LRU. Discovery and drawable decoding run on one worker thread. Results return to the UI only while active and for the current request generation. Icons are downsampled to at most 160×160 and stale recycled-view results are ignored. A fallback always renders if an icon cannot load. No network queries are involved.

`FavouriteOrder`: pure ordering/filtering rules, exercised by unit tests. Unavailable packages are hidden rather than erased from persisted order. Corrupt preferences recover to an empty list without crashing; the All apps tile remains reachable.

## Discovery and safety

Intent queries declare TV and ordinary launcher categories without QUERY_ALL_PACKAGES. Disabled, unexported, inaccessible and self components are excluded. System packages are not indiscriminately hidden: Settings, Play Store and similar useful apps remain eligible if they expose a real launcher activity. Entries are deduplicated by package, preferring the TV activity. No package names for streaming providers are hardcoded.

Package change broadcasts refresh discovery while visible; onStart also catches changes made while stopped. Time broadcasts update the optional clock once per minute only while visible. Broadcasts are protected Android system broadcasts; no application-defined exported broadcast commands are accepted.

Only public intents/APIs are used: Android Settings, App info, TvInputManager and TvContract passthrough VIEW URIs. No accessibility service, device-admin permission, network permission, boot receiver, background service, alarms, account, analytics, remote configuration or root functionality exists. No HDMI-CEC control or privileged power APIs are used.

## Compatibility

Minimum SDK 21 is provisional until the physical TV is audited. API 24 input visibility and API 28 accessibility heading are guarded; API 33 receiver flags are version checked. API compatibility is lint checked. Target SDK is 35, compile SDK 36. Back opts out of predictive dispatch to preserve explicit HOME/task behavior with the classic Activity callback. TV remote behavior and vendor launch policy still require the Kodak tests.

Screen recreation follows Android’s normal Activity state restoration. No permanent reference to an Activity is retained. The worker is shut down on destruction, cached icons are evicted under memory pressure, and dialogs close when the Activity stops.

## Known scope limits

The current UI is English. Phone apps may not support D-pad controls after launch. Protected or absent TV input activities cannot be made functional by a normal launcher. Firmware may intercept HOME; Still TV does not bypass it. Launch readiness includes asynchronous app discovery, so `am start -W` alone is not a full readiness measurement. APK size and emulator memory are not TV performance evidence.

## Primary references

- [Android TV app manifest and setup](https://developer.android.com/training/tv/get-started/create)
- [Package visibility declarations](https://developer.android.com/training/package-visibility/declaring)
- [TvInputManager](https://developer.android.com/reference/android/media/tv/TvInputManager)
- [Android Gradle Plugin 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
