# The Android logger

The app records location and sensor data while a vehicle is moving and uploads it to `ingest`. It is written to live in a car unattended - typically an old handset wired to the car's power - rather than to be opened and driven by hand.

## What it is doing at any moment

The logger has three states, shown at the top of its screen and in its notification.

| State | Meaning |
| ----- | ------- |
| `STOPPED` | Switched off. Nothing is recorded and nothing is watching. |
| `WAITING FOR MOVEMENT` | On duty but parked. Sensors and GPS are off and only the hardware significant-motion sensor is listening, which costs almost nothing. |
| `RECORDING` | Moving. Sensors, GPS and the flush loop are running, and the CPU is held awake. |

The button switches the logger on and off. What it does once on is decided by movement and by the power settings, which is why "on" does not always mean "recording".

Movement promotes it from waiting to recording, but GPS then has to agree: if no fix shows the vehicle actually travelling within a short window, it goes back to waiting. That is what stops a door slamming, or the phone being picked up, from recording a journey that never happened.

## As the battery drains

Nothing is given up while the phone is on power. Off power, the logger sheds work in the order of what each part costs against what it is worth - uploads first, because the radio is the most expensive thing it does and nothing is lost by waiting; then the sample rate; then the sensors that only decorate a position; and only last does recording stop. Whichever tier is in force is named on screen, so being cut back does not look like being broken.

## Android versions

| Setting      | API level | Android version |
| ------------ | --------- | --------------- |
| `minSdk`     | 28        | 9               |
| `targetSdk`  | 28        | 9               |
| `compileSdk` | 37        | -               |

The app installs on Android 9 and later and declares itself an Android 9 app, so a newer phone runs it under Android 9's rules rather than its own. `compileSdk` only decides which APIs the code can see when it is built; it does not change either of those.

Both are kept at 28 for compatibility with older phones, which is what the app is meant to run on - a retired handset left in the car. The lint warning about the old target is suppressed in `app/build.gradle.kts` for that reason.

The cost is Google Play, which does not accept apps or updates that target a level this old. The app is distributed through GitHub instead: the plan is for CI to publish signed APKs to GitHub Releases, for Obtainium to pick up on the phone, and [`tasks/publish-signed-builds-to-github-releases-for-obtainium.md`](../tasks/publish-signed-builds-to-github-releases-for-obtainium.md) describes it. Until that exists, builds are installed with `adb install`. [`tasks/declare-a-foreground-service-type-before-raising-the-target-sdk.md`](../tasks/declare-a-foreground-service-type-before-raising-the-target-sdk.md) lists what has to change in the manifest if the target is ever raised.

## Platform limitations

**A force-stopped app does not come back on its own.** If the app is stopped from Android's own application settings, the system puts the package into a stopped state in which it receives no broadcasts at all - not `BOOT_COMPLETED`, not `MY_PACKAGE_REPLACED`. "Auto-start on boot" therefore cannot recover it, and neither can rebooting the phone. Only opening the app by hand clears that state.

This is how Android treats a stopped package and there is nothing the app can do about it. It is worth knowing because the symptom - a phone that sat in a car for a week and recorded nothing - looks exactly like a bug in the app's own restoration. From Android 15 opening the app by hand also delivers the `BOOT_COMPLETED` it missed, so the logger comes back the moment the app is opened rather than after the next reboot.

**From Android 13, Stop in Active apps ends logging too.** The notification panel lists apps with a running foreground service, each with a Stop button. It kills the app, and Android does not restart the service - `START_STICKY` is not honoured - so the logger stays down until the phone restarts or the app is opened and logging started again. Nothing tells the app at the time; when it next runs, Android's record of how the previous process ended says so. The screen then explains why logging is not running, and `service_started` reports the reason to the server, along with any other way the previous process ended - a crash, a kill for memory, a revoked permission.

**Battery settings decide whether the logger is allowed to run at all.** Android 12 introduced a "Restricted" level for an app's battery use, which the user can choose and Android can also apply by itself to an app it judges to use too much. A restricted app may not run a foreground service: choosing it stops the logger at once, and it cannot start again after a reboot. Short of that, the default "Optimized" lets Doze hold the logger's work back while the phone is idle. The screen says when either applies; "Optimized" is fixed with Android's own one-tap dialog for "Unrestricted", "Restricted" by changing it in the app's battery settings, which the warning opens.

**Some manufacturers stop background apps regardless.** Samsung, Xiaomi, Huawei and OnePlus among others add battery management of their own, beyond anything Android does, with settings an app can neither see nor change. The screen links to dontkillmyapp.com's page for the phone's manufacturer, which lists them; it opens in the browser and the app sends nothing.

**From Android 12, location can be approximate.** The user may switch off "Use precise location", leaving positions that can be a kilometre or more out. The screen says so and asks for precise location again.

**Data Saver holds back uploads over mobile data.** With Data Saver on, an app that is not exempt sends nothing over mobile data in the background. This only matters with "Wi-Fi only" off, which is the only time the screen mentions it; its button opens the app's exemption directly.

**From Android 16, uploads may be cut short while the logger runs.** Background jobs running at the same time as a foreground service now count against a runtime quota. Uploads are WorkManager jobs and the logger is a foreground service, so a long backlog may be sent in several sittings rather than one. Nothing is lost - the rows wait - and there is no setting that changes it.

**Installing shows a warning.** Play Protect warns at install time about any app targeting more than two levels below the phone's Android version: "This app was built for an older version of Android". It is expected for this app, which targets Android 9 on purpose, and can be dismissed.

**From Android 11, a reboot needs "Allow all the time" location.** After a reboot `BootReceiver` starts the logger with nothing on screen, and Android gives a service started that way location only if the app may use it in the background - whatever the app targets. With "while using the app" alone the logger still starts and still records, only without a position in any row. The screen says so and offers to fix it; on Android 11 and later the choice itself is made on the app's location page in system settings, which the offer links to. Android 9 has no such distinction.

**From Android 13, notifications start switched off.** Posting one became a permission, and an app targeting an earlier level cannot ask for it - Android shows no dialog. Until it is switched on in the app's notification settings, neither the logger's own notification nor the warning that uploads have stopped is ever seen. The screen says so, and its button opens that settings page directly.

**Cleartext uploads are a debug-build affordance.** Release builds do not permit plain HTTP, so a server reached over `http://` works only from a debug build. See [`tasks/allow-cleartext-to-a-private-address-and-only-to-a-private-address.md`](../tasks/allow-cleartext-to-a-private-address-and-only-to-a-private-address.md) for the intended relaxation, which would allow cleartext to private addresses only.

## Debug and release side by side

The two builds install as separate apps: release as `com.anonymus09.carsensors`, debug as `com.anonymus09.carsensors.debug`, shown in the launcher as "carSensors debug". One phone can carry both - the release build doing the real logging, the debug build there to try something out - and neither can touch the other's database, settings or pairing. That includes uninstalling: a run of the instrumented tests installs the debug build and removes it afterwards, which leaves the release build and its backlog alone.

Each is a logger of its own, though. Both left switched on means two sets of rows, uploaded under whatever each is paired as, so the debug build is best left stopped, or unpaired, on a phone that is logging for real. Its version name ends in `-debug`, which reaches the server in the User-Agent.

Builds installed before this split carry the release id while being debug-signed. A debug build no longer updates them - it installs beside them - and the first signed release build cannot either; see [`tasks/publish-signed-builds-to-github-releases-for-obtainium.md`](../tasks/publish-signed-builds-to-github-releases-for-obtainium.md).

## Working on it

Three tools guard the Kotlin, mirroring what `www` already has: **ktlint** for formatting, which is Prettier's counterpart; **detekt** for code smells, which is ESLint's; and **Android Lint**, which catches platform mistakes neither of the others can see. All three run on every pull request that touches `android/`.

Android Studio needs nothing installed to work with this. `android/.editorconfig` is read by the IDE and by ktlint alike, so the formatter produces code ktlint already accepts rather than code it then rejects - which is the usual friction when a project adds a linter. It is deliberately scoped to the Android tree by `root = true`, so it cannot reach `www/` and its Prettier settings.

The style it pins is `android_studio`, the one the IDE's formatter produces. For a while ktlint checked only the build scripts: ktlint-gradle 13 looks for Kotlin sources through the `kotlin-android` plugin, which AGP 9 no longer uses, so it found none and passed. Version 14 finds them. A check that passes is not the same as a check that ran - if a ktlint task reports `NO-SOURCE` for `main`, it is not looking at the app.

Two run configurations are shared through `.idea/runConfigurations/` and appear in the Run menu without anything having to be typed:

| Configuration | Runs |
| ------------- | ---- |
| **Verify (lint + tests)** | `ktlintCheck detekt lintDebug testDebugUnitTest` - what CI will run |
| **Format (ktlint)** | `ktlintFormat` - fixes what can be fixed automatically |

The same tasks are in the Gradle tool window under `app/` if you would rather find them there.

Instrumented tests are a separate matter, because they need a device. Locally, `./gradlew connectedDebugAndroidTest` runs them against whatever is plugged in, which is both the fastest way and the most faithful one. CI has no handset, so it uses a Gradle Managed Device - declared in `build.gradle.kts` rather than in the workflow, so the same declaration serves both - and runs `api30atdDebugAndroidTest`. Only a change that could affect a migration - which includes the dependencies, as [the pinned kotlinx-serialization](#why-the-instrumented-tests-pin-kotlinx-serialization) explains - or a change to the instrumented tests themselves, triggers it on a pull request.

To check the app across Android versions, `./gradlew allApisGroupDebugAndroidTest` runs every instrumented test on API 28, 30, 33 and 37 in turn, creating each emulator and shutting it down afterwards. API 33 is where notifications became a permission and API 37 is the newest release - two places where the platform changes behaviour for this app whatever it targets. `TelemetryRecordingTest` is what that matters most for: it feeds the logger GPS fixes from a mock provider and checks that they land in the database, both when the logger is started from the screen and when it is started in the background, as after a reboot. It runs on emulators only, because on a real handset its invented positions would join the backlog and be uploaded.

CI uses those devices in two stages. A pull request waits for `api30atd` alone, because it is the stripped-down image and therefore the quick one. After a merge, the same tests run on `api28` - the level this app targets and the handset actually runs - and on `api33atd` and `api37`, where newer Android changes this app's behaviour whatever it targets; of these only `api33atd` uses a stripped-down image, and each runs on a runner of its own. Running the slower devices after the merge rather than on the pull request means they hold nobody up, while still being something that happens on its own: a check that runs only when somebody remembers is worth about as much as a backup taken the same way. A merge rather than a schedule, because what they guard against can only arrive with a code change - a calendar would fire when nothing had happened and stay quiet when something had. The workflow can also be started by hand from the Actions tab, on any branch. The runners ship no emulator, so the workflow installs one itself rather than rely on a system image to bring it in: API 28's image does not, which is why its device used to fail to set itself up there.

Nothing about the devices is cached. The emulator and each system image are downloaded on every run, which takes under a minute even for API 37's 2 GB image; caching them would cost more of the repository's 10 GB cache than it saves in time. A device's setup is a step of its own that is retried up to three times, since a download once arrived corrupt; the tests themselves are never retried, so a flaky test still fails. The Gradle cache is written by one job only: "Android - validation" saves it, and the emulator jobs and the dependency graph restore it read-only, the emulator jobs falling back to the newest entry because they have none of their own. The dependency graph could look like the natural writer, but it resolves metadata without compiling anything, so its cache holds a sixth of the jars the others need. That is why validation also builds the instrumented test APK - its cache then holds what the emulator jobs need, and a change that stops the instrumented tests compiling fails there, on every pull request, rather than only when the migration workflow happens to run.

The steps the Android jobs share live in two composite actions, as `www`'s do in `setup-www`. `.github/actions/setup-android` installs the JDK, sets up Gradle and accepts the SDK licences, for every Android job; `.github/actions/setup-android-device` installs the emulator, enables KVM and sets up a managed device, for the emulator jobs. A workflow that uses one lists it in its `paths:` filter, so changing the action runs the workflows it affects.

Two details worth knowing. detekt's baseline, at `android/config/detekt/baseline.xml`, records three findings that are real rather than false: the foreground service is a large class with too many functions and one long method. They are grandfathered so that anything *new* still fails, and [`tasks/split-the-foreground-service-up.md`](../tasks/split-the-foreground-service-up.md) describes the split that would clear them. And `NewerVersionAvailable` and `GradleDependency` are disabled in the lint configuration, because they report what has been published since rather than anything about this code, and would turn a passing build red without a commit being made. `GradleDependency` hid that for a while: lint's result is cached, so it failed only once something invalidated the cache, and any change to the version catalog does. Renovate proposes those updates anyway.

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
