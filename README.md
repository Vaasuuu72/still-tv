# Still TV

A native Kotlin launcher for Android TV: quiet, offline, remote-first, and free of advertising or telemetry. Built specifically for this project; no third-party launcher is used.

**Current status:** development build available; Kodak `PATH_UHDXANDROID` is not yet connected. Its Android version, hardware, input support and default-HOME policy remain unverified. Minimum supported API is provisionally 21, checked with Android Lint; adapt if the measured device requires it. Do not treat emulator results as Kodak validation.

## Build

The project uses Gradle 8.13, Android Gradle Plugin 8.13.0, Kotlin 2.1.20 and compile SDK 36. JDK 17+ is required (this Mac uses Android Studio’s JDK 21). Runtime dependencies consist of Kotlin’s standard library; no Compose, appcompat, networking SDK or permanent service.

```sh
./scripts/build.sh
```

Configure `sdk.dir` in untracked `local.properties` or set `ANDROID_HOME`. The script finds Android Studio’s bundled Java on macOS when `JAVA_HOME` is unset. Missing build dependencies are fetched by Gradle. SDK licenses must already be accepted through the normal SDK tools.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Preview build** (installs next to the production launcher and never registers as HOME):

```sh
./gradlew -Pstill.preview=true :app:assembleDebug   # → app/build/outputs/apk/debug/still-tv-preview-debug.apk
```

Package `tv.still.launcher.preview`, label "Still TV Preview". See `reports/still-tv-2-release-readiness.md` for the preview → production update and rollback steps.

## Use

- Arrows select; OK opens; hold OK or press Menu to manage an application.
- Pin, unpin, move earlier/later, or open Android’s App info screen.
- All apps includes exported, enabled TV and standard launcher activities; TV entry points take precedence. Phone-only apps may themselves require touch.
- Back returns from All apps; Back on the home page keeps the launcher visible.
- Settings opens the existing Android settings app.
- Inputs lists public passthrough TV inputs when available. Actual switching depends on the firmware’s TV activity. Otherwise use the remote’s Input/Source button.
- Use the remote’s Power button; Still TV does not request power-control privileges.

The first successful discovery seeds up to seven apps alphabetically. After that, your exact favourites are respected, including an empty list. Removed/disabled apps disappear; their saved positions recover if they become available again.

## Connect and audit

See [deployment instructions](docs/deployment.md). No subnet scanning or assumed ADB port is used.

```sh
./scripts/connect-tv.sh list
./scripts/connect-tv.sh connect TV_IP:ACTUAL_DEBUG_PORT
./scripts/audit-tv.sh TV_IP:ACTUAL_DEBUG_PORT
```

The audit writes `reports/tv-baseline.md` and timestamped raw evidence. Repeated audits preserve the first captured baseline. Raw logs may contain personal app/session information and are excluded from Git.

## Test and deploy

```sh
./scripts/build.sh :app:assembleDebugAndroidTest
./scripts/test-emulator.sh emulator-5554
./scripts/install-tv.sh TV_SERIAL app/build/outputs/apk/debug/app-debug.apk reports/raw/BASELINE_DIRECTORY
```

The emulator runner opens Settings and sends remote key events. The install script only installs and opens Still TV manually; it never changes HOME or disables packages. Follow the physical-device checklist before selecting a default launcher.

## Restore the original launcher

**Kodak `PATH_UHDXANDROID` (current setup):** Still TV is HOME because Android TV Home is disabled. On this firmware Android TV Home's system priority overrides `set-home-activity`. To restore it, either:

- on the TV: Still TV → Settings → Apps → See all apps → Show system apps → **Android TV Home → Enable**; or
- `adb -s 192.168.1.8:5555 shell pm enable com.google.android.tvlauncher`

Android TV Home becomes HOME again immediately (verified). General instructions:

```sh
./scripts/rollback.sh TV_SERIAL reports/raw/BASELINE_DIRECTORY --open
```

That opens the recorded original HOME without changing defaults. Standard Android Home-app settings are the preferred default selection and recovery mechanism. Only after approval, if supported by the firmware:

```sh
./scripts/rollback.sh TV_SERIAL reports/raw/BASELINE_DIRECTORY --restore-default
```

Neither script uninstalls or disables anything. Do not remove Still TV while it is the selected HOME; first restore and test the original.

## Evidence and architecture

- [Local build and emulator validation](reports/local-validation.md)
- [Baseline and connection status](reports/tv-baseline.md)
- [Package safety audit](reports/bloatware-audit.md)
- [Performance comparison](reports/performance-comparison.md)
- [Architecture](docs/architecture.md)
- [Deployment and physical test checklist](docs/deployment.md)

This debug APK uses the local development signing key. Keep that key for compatible upgrades; production signing and store distribution are outside this local deployment.
