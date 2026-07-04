# AGENTS.md

## Cursor Cloud specific instructions

This is a native **Android app** (Idle Fantasy) written in Kotlin + Jetpack Compose, built with Gradle. There is no backend or web service — the only "application" is the Android APK.

### Environment (already provisioned in the VM snapshot)

- **JDK 17** is the system default (`java`/`javac` via `update-alternatives`). The project targets JVM 17; do not build with a different JDK.
- **Android SDK** lives at `/opt/android-sdk` (`platforms;android-35`, `build-tools;35.0.0`, `platform-tools`, `emulator`, `system-images;android-35;google_apis;x86_64`). Interactive shells get `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` and PATH from `~/.bashrc`.
- The startup update script writes `local.properties` (`sdk.dir=/opt/android-sdk`) and warms Gradle dependencies, so Gradle finds the SDK even in non-interactive shells.

### Build / test / lint (these mirror CI in `.github/workflows/ci.yml`)

Run from the repo root; the Gradle wrapper (`./gradlew`, Gradle 8.7) is the source of truth:

- Unit tests: `./gradlew testDebugUnitTest` (JVM + Robolectric, no device needed). Core gameplay logic lives under `app/src/test/kotlin/com/fantasyidler/simulator/` (skills, combat, XP, mercantile) and `data/db/MigrationTest.kt`.
- Lint: `./gradlew lintDebug` (a `lint-baseline.xml` suppresses the pre-existing backlog; CI only fails on new issues).
- Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- First `testDebugUnitTest`/`assembleDebug` after a cold cache takes a few minutes (KSP + Hilt + Room codegen); subsequent runs are fast.

### Running the app on the emulator — important caveats

There is **no KVM** (`/dev/kvm` is absent) in the Cloud VM, so the Android emulator runs with pure software rendering (`-accel off -gpu swiftshader_indirect`). This works but is **extremely slow and unstable**:

- Cold boot of the AVD `if_test` takes ~2.5 min (first ever boot is ~13 min due to system dex2oat; that is cached afterward).
- Start it windowed on the VNC desktop so it is visible/interactable via computer-use:
  `DISPLAY=:1 $ANDROID_HOME/emulator/emulator -avd if_test -no-audio -no-boot-anim -accel off -gpu swiftshader_indirect -no-snapshot`
- `adb shell screencap` returns an all-black image for the app's Compose surface (host swiftshader "bad color buffer handle"). Use the **windowed emulator + computer-use screenshots** to see the UI, not `screencap`.
- Compose first-frame is very slow: `ActivityTaskManager: Displayed ... MainActivity` logs `+50s`–`+1m20s` for the first paint. A momentarily black screen is rendering, not a crash. Wait patiently.
- Under sustained rendering load the whole emulated Android system can crash (`systemui crashed too many times`, `DeadSystemException: The system died`). This is an emulator resource limit, not an app bug. A fresh cold boot restores a stable window for a while.
- Suppress background-app ANR dialogs with `adb shell settings put global hide_error_dialogs 1`.
- The app requests `POST_NOTIFICATIONS`; grant it non-interactively with `adb shell pm grant com.tristinbaker.idlefantasy android.permission.POST_NOTIFICATIONS`.
- To skip the first-run onboarding/tutorial (which is flaky to tap through on the slow emulator), seed the flag in the app DB (debug build, so `run-as` works):
  `echo "INSERT OR REPLACE INTO global_state (key,value,updated_at) VALUES ('onboarding_complete','true',0);" | adb shell run-as com.tristinbaker.idlefantasy sqlite3 databases/fantasy_idler.db`

Because interactive GUI testing is unreliable here, prefer the JVM unit tests (especially the `simulator` package) to validate core game logic; use the emulator mainly to confirm the app installs, launches, and renders.
