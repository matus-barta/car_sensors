---
title: "Add a download section to the README once there are releases"
status: blocked
area: docs
depends_on:
  - publish-signed-builds-to-github-releases-for-obtainium
---

Projects like this one put getting the app near the top of their README -
[GPSLogger](https://github.com/mendhak/gpslogger) opens with a Download
section, [Hauk](https://github.com/bilde2910/Hauk) with "get it on" buttons -
and this README cannot yet, since there is nothing to download: its status line
says the Android app is installed from a build with `adb`.

**Unblocked when** signed builds are published to GitHub Releases, as
[Publish signed builds to GitHub Releases for Obtainium](publish-signed-builds-to-github-releases-for-obtainium.md)
describes. Then the README gains, below its opening:

- where to get the app - the latest release, and how to add the repository to
  Obtainium so the phone updates itself;
- how to check that an APK is the one the project signed, with the
  certificate's fingerprint, as GPSLogger's
  [Verifying](https://github.com/mendhak/gpslogger#verifying) section does;
- the status line, rewritten for a project that has releases.
