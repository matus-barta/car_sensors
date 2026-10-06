---
title: "Link a primary source for each Android platform claim"
status: backlog
area: docs
depends_on: []
---

The Android pages say a good deal about how Android and Google Play behave,
and almost none of it links to where that is stated. `/verify-docs` can
settle a claim about this repository from the code, but a claim about the
platform only from its source - so these came back unverifiable, and will
again each time the pages are checked, until each links the documentation
that says so.

In [`docs/overview/android-app.md`](../overview/android-app.md):

- Google Play does not accept apps or updates that target a level as old as
  the app's.
- A force-stopped package receives no broadcasts, not `BOOT_COMPLETED` nor
  `MY_PACKAGE_REPLACED`, until it is opened by hand - and from Android 15,
  opening it delivers the `BOOT_COMPLETED` it missed.
- From Android 13, Stop in Active apps kills the app and `START_STICKY` is not
  honoured.
- Android 12's "Restricted" battery level forbids a foreground service, and
  Android may apply it by itself.
- From Android 12, location can be approximate.
- From Android 16, background jobs running beside a foreground service count
  against a runtime quota.
- Play Protect warns about an app targeting more than two levels below the
  phone's Android version.
- From Android 11, a service started at boot gets location only with "Allow
  all the time", whatever the app targets.
- From Android 13, posting a notification is a permission an app targeting an
  earlier level cannot ask for.

In [`docs/development/android/build.md`](../development/android/build.md):

- `allApisGroupDebugAndroidTest` runs the devices "in turn" - how many a
  managed device group runs at once is AGP's default, not something the build
  sets, so this wants AGP's documentation or a correction.
- API 37 is "the newest release".

Each wants a link to Android's developer documentation, the Play Console
policy or the AGP reference, beside the sentence it supports. A claim the
source does not bear out is corrected rather than linked. Once linked, run
`/verify-docs` on both pages so the claims are checked against those sources.
