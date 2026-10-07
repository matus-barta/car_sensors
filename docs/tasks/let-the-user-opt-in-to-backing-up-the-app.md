---
title: "Let the user opt in to backing up the app"
status: backlog
area: android
depends_on: []
---

The app opts out of Android's backup to Google Drive: `android:allowBackup` is
`false`, because the backup would otherwise carry the database of recorded
positions and the pairing credential off the phone - end-to-end encrypted only
on a phone with a screen lock ([Auto Backup](https://developer.android.com/identity/data/autobackup)).
[What leaves the phone](../overview/android-app.md#what-leaves-the-phone) says
why nothing there needs restoring.

Someone may still want it - to keep the settings across a new phone, say. That
can only be an opt-in, off by default and switched on in the app with what it
copies stated plainly. `allowBackup` is fixed in the manifest at build time, so
the choice has to be made in code when a backup runs, through a backup agent of
the app's own, which is also where it decides what goes: the settings, but
never the pairing credential, and the recorded positions only if the user says
so.
