# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Single-module Android app (`:app`, package `com.gg.barcodes`) built with Jetpack Compose + Material 3. It scans stickers that carry 2–4 barcodes close together, with the camera. Once every barcode is reliably decoded, it freezes an annotated copy of the camera frame and lists the values for copying to the clipboard. UI strings are Bulgarian (`res/values/strings.xml`).

## Architecture

Pipeline: CameraX `LifecycleCameraController` → `BarcodeAnalyzer` → `ScanViewModel` → `ConsensusTracker` → `ScanResult`.

- `scan/BarcodeAnalyzer.kt`: converts each RGBA frame into an upright `Bitmap` and decodes it with ML Kit (bundled model) for the formats picked in settings (`ScanFormat`, default Code 128, EAN-13 and QR, the formats the stickers use; the scanner is rebuilt only when the set changes). Every other frame it decodes a contrast-stretched, gamma-darkened grayscale copy instead, for faded print. The same bitmap is decoded and kept, so detection boxes are in that bitmap's pixel space. It blocks on `Tasks.await` on the single analyzer executor, which provides the back-pressure.
- `scan/BarcodeValue.kt`: pure Kotlin, unit-tested. `ScanViewModel` runs every decoded value through it before the tracker. It always removes a leading AIM symbology identifier (e.g. `]C1` that ML Kit prepends to GS1-128). With the "strip labels" setting (on by default) it also removes `S/N:`/`P/N:`-style labels.
- `scan/ConsensusTracker.kt`: pure Kotlin with no Android types, unit-tested in `app/src/test`. This is where accuracy comes from. Each physical barcode is a *track*, matched across frames by IoU/proximity, then by value. Each frame casts a vote. A track is confirmed only when its leading value is ahead of the runner-up by ≥ `requiredVotes` votes, so scattered misreads of a faded barcode don't stall it. Scanning completes when the confirmed count reaches the user-selected expected count (2/3/4). In "Auto" mode it instead waits for no new track to appear for `settleFrames`. Any visible, still-unconfirmed track blocks completion.
- `scan/ScanViewModel.kt`: owns the analyzer executor and tracker. It keeps the frame in which the most tracks were visible as the result image, draws numbered boxes on it (`annotate`), and exposes `live` (overlay state) and `result`. The result image is kept only in memory and is dropped on `restart()`. Tracker state is touched only on the analyzer thread; `restart()` communicates through volatile flags.
- `ui/ScanScreen.kt`: camera preview plus overlay. Preview and analysis share a 16:9 `ResolutionSelector`, so the overlay maps image coordinates to the view with a simple FILL_CENTER transform. Camera zoom applies to analysis frames too. It is set by pinch, presets or a slider (capped at `MaxUsefulZoom`) and saved in `ScanViewModel.zoomRatio`, which is restored on the first `zoomState` emission after binding. `ui/ResultScreen.kt` shows the annotated image and copy actions. Values that `scan/WifiCode.kt` parses as `WIFI:` QR codes are shown as name, password and security, each copyable, plus the raw text. The "readable Wi-Fi" setting turns this off. The password is copied as sensitive clipboard content. "Copy all" always copies the raw values. `ui/SettingsScreen.kt` picks the scanned formats, saved in `scan/ScanSettings.kt` (SharedPreferences) and applied through `ScanViewModel.setFormats`, and opens the update dialog. `MainActivity` handles the camera permission and switches screens on `result` and the settings flag.
- `update/`: self-update from `apks.gg-webapp.com`, copied 1:1 from the reference app (`com.gg.smsparking`) per `docs/self-update.md`. Don't edit it locally; changes go to the standard. The only app-side code is `Updater.init` in `MainActivity`, plus `UpdateBanner` above the content and `UpdateDialog` (opened from `ui/SettingsScreen.kt`). Debug builds have updates disabled unless `-PupdateUrl=…` is passed.

## Commands

Use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # build and install on a connected device/emulator
./gradlew testDebugUnitTest        # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest  # instrumented tests (app/src/androidTest), needs a device
./gradlew lint                     # Android lint

# single test class / method
./gradlew testDebugUnitTest --tests "com.gg.barcodes.ExampleUnitTest"
./gradlew testDebugUnitTest --tests "com.gg.barcodes.ExampleUnitTest.addition_isCorrect"
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gg.barcodes.ExampleInstrumentedTest
```

## Build setup notes

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin — only `com.android.application` and `org.jetbrains.kotlin.plugin.compose`. Don't add the Kotlin Android plugin back.
- Dependencies and plugin versions are managed in the version catalog `gradle/libs.versions.toml`; reference them as `libs.*` in build scripts. Compose library versions come from the Compose BOM (no per-artifact versions).
- `compileSdk`/`targetSdk` 37, `minSdk` 29, Java 11 source/target. The Gradle daemon runs on a JDK 25 toolchain (`gradle/gradle-daemon-jvm.properties`, auto-provisioned via foojay).
- The launcher icon is the adaptive vector in `mipmap-anydpi`. `mipmap-xxxhdpi/ic_launcher*.webp` are rasters generated from it for the update server, which can't draw vectors. Regenerate them with `python3 scripts/render-launcher-icons.py` (macOS) after changing the icon. Keep xxxhdpi only, because the server takes the first raster.
- Release builds use the new `optimization {}` DSL (R8). Keep rules go in `app/src/main/keepRules/*.keep` (all files there are merged), not a `proguard-rules.pro`. `packageScope` limits R8 to androidx/kotlin/kotlinx; don't drop it, because full-app R8 breaks ML Kit internals (NPE in `BarcodeScanning.getClient`). `kotlin-multifile.keep` stops R8 from moving package-private Kotlin `*Kt__*` classes out of their package (start-up `IllegalAccessError`). Verify a release build on a device or emulator, not just that it compiles.
- Configuration cache is enabled in `gradle.properties`; build logic must stay configuration-cache compatible.
- Repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add `repositories {}` blocks in module build files.
