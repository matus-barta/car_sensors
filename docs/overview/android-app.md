---
title: "The Android logger"
---

The app records location and sensor data while a vehicle is moving and uploads it to `ingest`. It is written to live in a car unattended - typically an old handset wired to the car's power - rather than to be opened and driven by hand.

This page is about what the app does on a phone. Building, checking and testing it is in [`docs/development/android/README.md`](../development/android/README.md).

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

## What leaves the phone

Only what it records, and only to the server address set in the app. There is no analytics or crash reporting, and the app opts out of Android's backup to Google Drive, which would otherwise copy its database of positions and its pairing credential there - end-to-end encrypted only on a phone with a screen lock ([Auto Backup](https://developer.android.com/identity/data/autobackup)). The phone holds only what it has not uploaded yet, so there is nothing a restore would save. The one link it offers to an outside site, a manufacturer's page on dontkillmyapp.com, opens in the browser only when tapped.

## Android versions

The app installs on Android 9 and later and declares itself an Android 9 app, so a newer phone runs it under Android 9's rules rather than its own. That is kept for compatibility with older phones, which is what the app is meant to run on - a retired handset left in the car.

The cost is Google Play, which does not accept apps or updates that target a level this old. The app is distributed through GitHub instead: the plan is for CI to publish signed APKs to GitHub Releases, for Obtainium to pick up on the phone, and [`docs/tasks/publish-signed-builds-to-github-releases-for-obtainium.md`](../tasks/publish-signed-builds-to-github-releases-for-obtainium.md) describes it. Until that exists, builds are installed with `adb install`.

The exact API levels, and what raising them would take, are in [`docs/development/android/build.md`](../development/android/build.md#android-versions).

## Platform limitations

**A force-stopped app does not come back on its own.** If the app is stopped from Android's own application settings, the system puts the package into a stopped state in which it receives no broadcasts at all - not `BOOT_COMPLETED`, not `MY_PACKAGE_REPLACED`. "Auto-start on boot" therefore cannot recover it, and neither can rebooting the phone. Only opening the app by hand clears that state.

This is how Android treats a stopped package and there is nothing the app can do about it. It is worth knowing because the symptom - a phone that sat in a car for a week and recorded nothing - looks exactly like a bug in the app's own restoration. From Android 15 opening the app by hand also delivers the `BOOT_COMPLETED` it missed, so the logger comes back the moment the app is opened rather than after the next reboot.

**From Android 13, Stop in Active apps ends logging too.** The notification panel lists apps with a running foreground service, each with a Stop button. It kills the app, and Android does not restart the service - `START_STICKY` is not honoured - so the logger stays down until the phone restarts or the app is opened and logging started again. Nothing tells the app at the time; when it next runs, Android's record of how the previous process ended says so. The screen then explains why logging is not running, and `service_started` reports the reason to the server, along with any other way the previous process ended - a crash, a kill for memory, a revoked permission.

**Battery settings decide whether the logger is allowed to run at all.** Android 12 introduced a "Restricted" level for an app's battery use, which the user can choose and Android can also apply by itself to an app it judges to use too much. A restricted app may not run a foreground service: choosing it stops the logger at once, and it cannot start again after a reboot. Short of that, the default "Optimized" lets Doze hold the logger's work back while the phone is idle. The screen says when either applies; "Optimized" is fixed with Android's own one-tap dialog for "Unrestricted", "Restricted" by changing it in the app's battery settings, which the warning opens.

**Some manufacturers stop background apps regardless.** Samsung, Xiaomi, Huawei and OnePlus among others add battery management of their own, beyond anything Android does, with settings an app can neither see nor change. The screen links to [dontkillmyapp.com](https://dontkillmyapp.com)'s page for the phone's manufacturer, which lists them; it opens in the browser and the app sends nothing.

**From Android 12, location can be approximate.** The user may switch off "Use precise location", leaving positions that can be a kilometre or more out. The screen says so and asks for precise location again.

**Data Saver holds back uploads over mobile data.** With Data Saver on, an app that is not exempt sends nothing over mobile data in the background. This only matters with "Wi-Fi only" off, which is the only time the screen mentions it; its button opens the app's exemption directly.

**From Android 16, uploads may be cut short while the logger runs.** Background jobs running at the same time as a foreground service now count against a runtime quota. Uploads are WorkManager jobs and the logger is a foreground service, so a long backlog may be sent in several sittings rather than one. Nothing is lost - the rows wait - and there is no setting that changes it.

**Installing shows a warning.** Play Protect warns at install time about any app targeting more than two levels below the phone's Android version: "This app was built for an older version of Android". It is expected for this app, which targets Android 9 on purpose, and can be dismissed.

**From Android 11, a reboot needs "Allow all the time" location.** After a reboot `BootReceiver` starts the logger with nothing on screen, and Android gives a service started that way location only if the app may use it in the background - whatever the app targets. With "while using the app" alone the logger still starts and still records, only without a position in any row. The screen says so and offers to fix it; on Android 11 and later the choice itself is made on the app's location page in system settings, which the offer links to. Android 9 has no such distinction.

**From Android 13, notifications start switched off.** Posting one became a permission, and an app targeting an earlier level cannot ask for it - Android shows no dialog. Until it is switched on in the app's notification settings, neither the logger's own notification nor the warning that uploads have stopped is ever seen. The screen says so, and its button opens that settings page directly.

**Cleartext uploads are a debug-build affordance.** Release builds do not permit plain HTTP, so a server reached over `http://` works only from a debug build. See [`docs/tasks/allow-cleartext-to-a-private-address-and-only-to-a-private-address.md`](../tasks/allow-cleartext-to-a-private-address-and-only-to-a-private-address.md) for the intended relaxation, which would allow cleartext to private addresses only.
