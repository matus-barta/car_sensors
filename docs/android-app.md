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

The cost is Google Play, which does not accept apps or updates that target a level this old. The app is distributed through GitHub instead: the plan is for CI to publish signed APKs to GitHub Releases, for Obtainium to pick up on the phone, and `todo.md` describes it under "Publish signed builds to GitHub Releases for Obtainium". Until that exists, builds are installed with `adb install`. `todo.md` also lists what has to change in the manifest if the target is ever raised, under "Declare a foreground service type before raising the target SDK".

## Platform limitations

**A force-stopped app does not come back on its own.** If the app is stopped from Android's own application settings, the system puts the package into a stopped state in which it receives no broadcasts at all - not `BOOT_COMPLETED`, not `MY_PACKAGE_REPLACED`. "Auto-start on boot" therefore cannot recover it, and neither can rebooting the phone. Only opening the app by hand clears that state.

This is how Android treats a stopped package and there is nothing the app can do about it. It is worth knowing because the symptom - a phone that sat in a car for a week and recorded nothing - looks exactly like a bug in the app's own restoration.

**From Android 11, a reboot needs "Allow all the time" location.** After a reboot `BootReceiver` starts the logger with nothing on screen, and Android gives a service started that way location only if the app may use it in the background - whatever the app targets. With "while using the app" alone the logger still starts and still records, only without a position in any row. The screen says so and offers to fix it; on Android 11 and later the choice itself is made on the app's location page in system settings, which the offer links to. Android 9 has no such distinction.

**Cleartext uploads are a debug-build affordance.** Release builds do not permit plain HTTP, so a server reached over `http://` works only from a debug build. See `todo.md` for the intended relaxation, which would allow cleartext to private addresses only.

## Working on it

Three tools guard the Kotlin, mirroring what `www` already has: **ktlint** for formatting, which is Prettier's counterpart; **detekt** for code smells, which is ESLint's; and **Android Lint**, which catches platform mistakes neither of the others can see. All three run on every pull request that touches `android/`.

Android Studio needs nothing installed to work with this. `android/.editorconfig` is read by the IDE and by ktlint alike, so the formatter produces code ktlint already accepts rather than code it then rejects - which is the usual friction when a project adds a linter. It is deliberately scoped to the Android tree by `root = true`, so it cannot reach `www/` and its Prettier settings.

Two run configurations are shared through `.idea/runConfigurations/` and appear in the Run menu without anything having to be typed:

| Configuration | Runs |
| ------------- | ---- |
| **Verify (lint + tests)** | `ktlintCheck detekt lintDebug testDebugUnitTest` - what CI will run |
| **Format (ktlint)** | `ktlintFormat` - fixes what can be fixed automatically |

The same tasks are in the Gradle tool window under `app/` if you would rather find them there.

Instrumented tests are a separate matter, because they need a device. Locally, `./gradlew connectedDebugAndroidTest` runs them against whatever is plugged in, which is both the fastest way and the most faithful one. CI has no handset, so it uses a Gradle Managed Device - declared in `build.gradle.kts` rather than in the workflow, so the same declaration serves both - and runs `api30atdDebugAndroidTest`. Only a change that could affect a migration, or a change to the instrumented tests themselves, triggers it on a pull request.

To check the app across Android versions, `./gradlew allApisGroupDebugAndroidTest` runs every instrumented test on API 28, 30, 33 and 37 in turn, creating each emulator and shutting it down afterwards. API 33 is where notifications became a permission and API 37 is the newest release - two places where the platform changes behaviour for this app whatever it targets. `TelemetryRecordingTest` is what that matters most for: it feeds the logger GPS fixes from a mock provider and checks that they land in the database, both when the logger is started from the screen and when it is started in the background, as after a reboot. It runs on emulators only, because on a real handset its invented positions would join the backlog and be uploaded.

CI is meant to use two of those devices rather than one. A pull request waits for `api30atd` alone, because it is the stripped-down image and therefore the quick one. After a merge, the same tests were meant to also run on `api28` - the level this app targets and the handset actually runs, for which no stripped-down image exists. Running the slower device after the merge rather than on the pull request means it holds nobody up, while still being something that happens on its own: a check that runs only when somebody remembers is worth about as much as a backup taken the same way. A merge rather than a schedule, because what it guards against can only arrive with a code change - a calendar would fire when nothing had happened and stay quiet when something had. That device is currently disabled in CI: its setup fails inside AGP, so API 28 is covered by the handset and not yet automatically - see `todo.md`.

Two details worth knowing. detekt's baseline, at `android/config/detekt/baseline.xml`, records three findings that are real rather than false: the foreground service is a large class with too many functions and one long method. They are grandfathered so that anything *new* still fails, and `todo.md` describes the split that would clear them. And `NewerVersionAvailable` is disabled in the lint configuration, because it reports what has been published since rather than anything about this code, and would turn a passing build red without a commit being made.

## Why the version catalog confuses other tools

Dependencies are declared in `android/gradle/libs.versions.toml`, a Gradle version catalog. The build files then refer to entries by name - `libs.androidx.core.ktx` - rather than carrying coordinates and versions themselves.

This is ordinary Gradle rather than anything Android-specific, and it has an ordinary consequence: a tool that reads build files sees dependencies with no versions, and a tool that assumes every `.toml` is a Cargo manifest looks the names up on crates.io and finds nothing. The `Dependi` extension for VS Code does the latter and reports that no versions were found for every package; Android Studio understands catalogs natively and is the better place to edit that file.

GitHub's own dependency graph has the first version of the problem, which matters more, because a dependency it cannot version is one it cannot raise a security advisory about. `ingest` and `www` are unaffected - a lockfile carries resolved versions - so this concerned only the Android tree. "Android - dependency graph" fixes it by asking Gradle to resolve the graph rather than parsing the files, and submits the result after a merge.

Renovate is unaffected either way: it reads the catalog as a first-class manifest and proposes updates to it directly.
