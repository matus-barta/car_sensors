---
title: "Working on the Android app"
---

How the Android app is built, checked and tested, and the parts of its build that are not what they first look like. What the app does, and the platform limitations it lives with, are in [`docs/overview/android-app.md`](../../overview/android-app.md).

## Android versions

| Setting      | API level | Android version |
| ------------ | --------- | --------------- |
| `minSdk`     | 28        | 9               |
| `targetSdk`  | 28        | 9               |
| `compileSdk` | 37        | -               |

`minSdk` and `targetSdk` are both kept at 28 for the older phones the app is meant to run on - [`docs/overview/android-app.md`](../../overview/android-app.md#android-versions) explains what that means on a phone. `compileSdk` only decides which APIs the code can see when it is built; it does not change either of those.

The lint warning about the old target is suppressed in `app/build.gradle.kts` for that reason. [`docs/tasks/declare-a-foreground-service-type-before-raising-the-target-sdk.md`](../../tasks/declare-a-foreground-service-type-before-raising-the-target-sdk.md) lists what has to change in the manifest if the target is ever raised.

## Debug and release side by side

The two builds install as separate apps: release as `com.anonymus09.carsensors`, debug as `com.anonymus09.carsensors.debug`, shown in the launcher as "carSensors debug". One phone can carry both - the release build doing the real logging, the debug build there to try something out - and neither can touch the other's database, settings or pairing. That includes uninstalling: a run of the instrumented tests installs the debug build and removes it afterwards, which leaves the release build and its backlog alone.

Each is a logger of its own, though. Both left switched on means two sets of rows, uploaded under whatever each is paired as, so the debug build is best left stopped, or unpaired, on a phone that is logging for real. Its version name ends in `-debug`, which reaches the server in the User-Agent.

Builds installed before this split carry the release id while being debug-signed. A debug build no longer updates them - it installs beside them - and the first signed release build cannot either; see [`docs/tasks/publish-signed-builds-to-github-releases-for-obtainium.md`](../../tasks/publish-signed-builds-to-github-releases-for-obtainium.md).

## Formatting and lint

Three tools guard the Kotlin, mirroring what `www` already has: **ktlint** for formatting, which is Prettier's counterpart; **detekt** for code smells, which is ESLint's; and **Android Lint**, which catches platform mistakes neither of the others can see. All three run on every pull request that touches `android/`.

Android Studio needs nothing installed to work with this. `android/.editorconfig` is read by the IDE and by ktlint alike, so the formatter produces code ktlint already accepts rather than code it then rejects - which is the usual friction when a project adds a linter. It is deliberately scoped to the Android tree by `root = true`, so it cannot reach `www/` and its Prettier settings.

The style it pins is `android_studio`, the one the IDE's formatter produces. For a while ktlint checked only the build scripts: ktlint-gradle 13 looks for Kotlin sources through the `kotlin-android` plugin, which AGP 9 no longer uses, so it found none and passed. Version 14 finds them. A check that passes is not the same as a check that ran - if a ktlint task reports `NO-SOURCE` for `main`, it is not looking at the app.

Two run configurations are shared through `.idea/runConfigurations/` and appear in the Run menu without anything having to be typed:

| Configuration | Runs |
| ------------- | ---- |
| **Verify (lint + tests)** | `ktlintCheck detekt lintDebug testDebugUnitTest` - what CI will run |
| **Format (ktlint)** | `ktlintFormat` - fixes what can be fixed automatically |

The same tasks are in the Gradle tool window under `app/` if you would rather find them there.

Two details worth knowing. detekt's baseline, at `android/config/detekt/baseline.xml`, records three findings that are real rather than false: the foreground service is a large class with too many functions and one long method. They are grandfathered so that anything *new* still fails, and [`docs/tasks/split-the-foreground-service-up.md`](../../tasks/split-the-foreground-service-up.md) describes the split that would clear them. And `NewerVersionAvailable` and `GradleDependency` are disabled in the lint configuration, because they report what has been published since rather than anything about this code, and would turn a passing build red without a commit being made. `GradleDependency` hid that for a while: lint's result is cached, so it failed only once something invalidated the cache, and any change to the version catalog does. Renovate proposes those updates anyway.

## Instrumented tests

Instrumented tests are a separate matter, because they need a device. Locally, `./gradlew connectedDebugAndroidTest` runs them against whatever is plugged in, which is both the fastest way and the most faithful one. CI has no handset, so it uses a Gradle Managed Device - declared in `build.gradle.kts` rather than in the workflow, so the same declaration serves both - and runs `api30atdDebugAndroidTest`. Only a change that could affect a migration - which includes the dependencies, as [the pinned kotlinx-serialization](#why-the-instrumented-tests-pin-kotlinx-serialization) explains - or a change to the instrumented tests themselves, triggers it on a pull request.

To check the app across Android versions, `./gradlew allApisGroupDebugAndroidTest` runs every instrumented test on API 28, 30, 33 and 37 in turn, creating each emulator and shutting it down afterwards. API 33 is where notifications became a permission and API 37 is the newest release - two places where the platform changes behaviour for this app whatever it targets. `TelemetryRecordingTest` is what that matters most for: it feeds the logger GPS fixes from a mock provider and checks that they land in the database, both when the logger is started from the screen and when it is started in the background, as after a reboot. It runs on emulators only, because on a real handset its invented positions would join the backlog and be uploaded.

## How CI runs them

CI uses those devices in two stages. A pull request waits for `api30atd` alone, because it is the stripped-down image and therefore the quick one. After a merge, the same tests run on `api28` - the level this app targets and the handset actually runs - and on `api33atd` and `api37`, where newer Android changes this app's behaviour whatever it targets; of these only `api33atd` uses a stripped-down image, and each runs on a runner of its own. Running the slower devices after the merge rather than on the pull request means they hold nobody up, while still being something that happens on its own: a check that runs only when somebody remembers is worth about as much as a backup taken the same way. A merge rather than a schedule, because what they guard against can only arrive with a code change - a calendar would fire when nothing had happened and stay quiet when something had. The workflow can also be started by hand from the Actions tab, on any branch. The runners ship no emulator, so the workflow installs one itself rather than rely on a system image to bring it in: API 28's image does not, which is why its device used to fail to set itself up there.

Nothing about the devices is cached. The emulator and each system image are downloaded on every run, which takes under a minute even for API 37's 2 GB image; caching them would cost more of the repository's 10 GB cache than it saves in time. A device's setup is a step of its own that is retried up to three times, since a download once arrived corrupt; the tests themselves are never retried, so a flaky test still fails. The Gradle cache is written by one job only: "Android - validation" saves it, and the emulator jobs and the dependency graph restore it read-only, the emulator jobs falling back to the newest entry because they have none of their own. The dependency graph could look like the natural writer, but it resolves metadata without compiling anything, so its cache holds a sixth of the jars the others need. That is why validation also builds the instrumented test APK - its cache then holds what the emulator jobs need, and a change that stops the instrumented tests compiling fails there, on every pull request, rather than only when the migration workflow happens to run.

The steps the Android jobs share live in two composite actions, `setup-android` for every Android job and `setup-android-device` for the emulator jobs, described with the others in [`docs/development/ci.md`](../ci.md#shared-setup-is-a-composite-action-one-purpose-each).

## Why the version catalog confuses other tools

Dependencies are declared in `android/gradle/libs.versions.toml`, a Gradle version catalog. The build files then refer to entries by name - `libs.androidx.core.ktx` - rather than carrying coordinates and versions themselves.

This is ordinary Gradle rather than anything Android-specific, and it has an ordinary consequence: a tool that reads build files sees dependencies with no versions, and a tool that assumes every `.toml` is a Cargo manifest looks the names up on crates.io and finds nothing. The `Dependi` extension for VS Code does the latter and reports that no versions were found for every package; Android Studio understands catalogs natively and is the better place to edit that file.

GitHub's own dependency graph has the first version of the problem, which matters more, because a dependency it cannot version is one it cannot raise a security advisory about. `ingest` and `www` are unaffected - a lockfile carries resolved versions - so this concerned only the Android tree. "Android - dependency graph" fixes it by asking Gradle to resolve the graph rather than parsing the files, and submits the result after a merge.

Renovate is unaffected either way: it reads the catalog as a first-class manifest and proposes updates to it directly.

## Why the instrumented tests pin kotlinx-serialization

`MigrationTest` reads the exported Room schemas through `room-testing`, which parses them with kotlinx-serialization. The two halves of that library reach the test classpath from different places: `kotlinx-serialization-json` comes from Room, and `kotlinx-serialization-core` from the lifecycle libraries the app itself ships - at an older version, which Gradle then holds the test classpath to strictly, so that tests run against what the app runs. A `core` older than `json` fails the moment a schema is read, with an `AbstractMethodError` that names neither library.

So `app/build.gradle.kts` forces all four artefacts - `core`, `json` and their `-jvm` variants - to one version, `kotlinxSerialization` in the catalog, and only on the instrumented test classpath; the app keeps exactly what its dependencies ask for. A force is the only thing that overrides the strict version: enforcing the kotlinx-serialization BOM instead makes resolution fail outright. Forcing only `core`, as an earlier version of this did, works until Room brings a newer `json` and leaves `core` behind again.

The catalog also lists `kotlinx-serialization-bom`, which nothing depends on. It is there because Renovate updates a version only through a library that refers to it, and a bare `kotlinxSerialization` would never move.

What catches a mistake here is `MigrationTest`, on a device. That is why the migration workflow runs on pull requests that change the catalog or the app's build file, not only the schemas: a dependency update can break it without touching anything else. Renovate's Kotlin rules also match `org.jetbrains.kotlin` followed by `.` or `:`, not the bare prefix, which once swept `org.jetbrains.kotlinx` into the Kotlin group and changed the forced version as a side effect of a compiler update.
