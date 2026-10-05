---
title: "Test what newer Android does to a logger nobody watches"
status: backlog
area: android
depends_on: []
---

The restricted battery state, approximate location, Data Saver and a stop from
Active apps have each been tried by hand on an API 33 emulator, but nothing
checks them automatically, and several other things have not been tried at all.
In rough order of how quietly they could end logging:

- **A real reboot.** `TelemetryRecordingTest` starts the logger from the
  background the way `BootReceiver` does, but nothing reboots a device and
  watches `BOOT_COMPLETED` arrive. An emulator can: `adb reboot`, with fixes fed
  through `adb emu geo fix`, once with "Allow all the time" and once without.
- **Doze.** `adb shell dumpsys deviceidle force-idle` while recording, then
  whether samples keep arriving, optimized and exempt.
- **Upload jobs under the Android 16 quota.** A backlog large enough to take a
  while, uploading on the API 37 device while the logger runs, recording
  `WorkInfo.getStopReason()` - the uploader does not record it today, which is
  worth doing anyway.
- **Notifications switched off before Android 13**, the one path to
  `access_changed` not yet seen to work.
- **A real handset from an aggressive manufacturer.** Samsung or Xiaomi stop
  apps in ways no emulator reproduces.
