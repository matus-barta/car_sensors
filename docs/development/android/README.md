---
title: "Working on the Android app"
---

How to check, test and install the Android app while working on it. Why the build is set up the way it is - the SDK levels, the linters' settings, how CI runs the devices - is in [`docs/development/android/build.md`](build.md); what the app does on a phone is in [`docs/overview/android-app.md`](../../overview/android-app.md).

## Checking the code

Three tools guard the Kotlin, mirroring what `www` already has: **ktlint** for formatting, which is Prettier's counterpart; **detekt** for code smells, which is ESLint's; and **Android Lint**, which catches platform mistakes neither of the others can see. All three run on every pull request that touches `android/`.

Android Studio needs nothing installed to work with this. `android/.editorconfig` is read by the IDE and by ktlint alike, so the formatter produces code ktlint already accepts rather than code it then rejects - which is the usual friction when a project adds a linter. It is deliberately scoped to the Android tree by `root = true`, so it cannot reach `www/` and its Prettier settings.

Two run configurations are shared through `.idea/runConfigurations/` and appear in the Run menu without anything having to be typed:

| Configuration | Runs |
| ------------- | ---- |
| **Verify (lint + tests)** | `ktlintCheck detekt lintDebug testDebugUnitTest` - what CI will run |
| **Format (ktlint)** | `ktlintFormat` - fixes what can be fixed automatically |

The same tasks are in the Gradle tool window under `app/` if you would rather find them there.

## Running the instrumented tests

Instrumented tests are a separate matter, because they need a device. Locally, `./gradlew connectedDebugAndroidTest` runs them against whatever is plugged in, which is both the fastest way and the most faithful one. How CI runs them, without a handset, is in [`docs/development/android/build.md`](build.md#how-ci-runs-the-instrumented-tests).

To check the app across Android versions, `./gradlew allApisGroupDebugAndroidTest` runs every instrumented test on API 28, 30, 33 and 37 in turn, creating each emulator and shutting it down afterwards. API 33 is where notifications became a permission and API 37 is the newest release - two places where the platform changes behaviour for this app whatever it targets. `TelemetryRecordingTest` is what that matters most for: it feeds the logger GPS fixes from a mock provider and checks that they land in the database, both when the logger is started from the screen and when it is started in the background, as after a reboot. It runs on emulators only, because on a real handset its invented positions would join the backlog and be uploaded.

## Debug and release side by side

The two builds install as separate apps: release as `com.anonymus09.carsensors`, debug as `com.anonymus09.carsensors.debug`, shown in the launcher as "carSensors debug". One phone can carry both - the release build doing the real logging, the debug build there to try something out - and neither can touch the other's database, settings or pairing. That includes uninstalling: a run of the instrumented tests installs the debug build and removes it afterwards, which leaves the release build and its backlog alone.

Each is a logger of its own, though. Both left switched on means two sets of rows, uploaded under whatever each is paired as, so the debug build is best left stopped, or unpaired, on a phone that is logging for real. Its version name ends in `-debug`, which reaches the server in the User-Agent.

Builds installed before this split carry the release id while being debug-signed. A debug build no longer updates them - it installs beside them - and the first signed release build cannot either; see [`docs/tasks/publish-signed-builds-to-github-releases-for-obtainium.md`](../../tasks/publish-signed-builds-to-github-releases-for-obtainium.md).
