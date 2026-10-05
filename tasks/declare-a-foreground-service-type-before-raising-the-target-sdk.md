---
title: "Declare a foreground service type before raising the target SDK"
status: backlog
area: android
depends_on: []
---

`targetSdk` is 28, and that is what keeps several things simple: a foreground
service needs no declared type, and cleartext is a manifest attribute rather
than a negotiation. Background location is not among them - Android 11 enforces
that whatever the target, so the app already asks for it separately.
Staying off the Play Store is what makes it tenable, since Play enforces a
minimum target version and nothing else does.

Should the target ever be raised - a newer handset, or a Play listing after
all - the service will need `android:foregroundServiceType="location"` in the
manifest and the `FOREGROUND_SERVICE_LOCATION` permission, or on API 34 and
above it will not be allowed to start at all.

App hibernation arrives with it too. From a target of Android 11 (API 30) up,
an app nobody opens for a few months has its permissions revoked - and from
Android 12 its jobs and alarms stopped - and a running foreground service does
not count as being used. A logger in a car is exactly an app nobody opens, so
raising the target means asking the user to exempt it, through
`IntentCompat.createManageUnusedAppRestrictionsIntent`, and saying on the screen
when it is not. At 28 none of this applies, which is one more thing the old
target is quietly doing.

None of this is work today; all of it is work on the day that number changes,
and it is better known in advance than discovered by a service that refuses to
start.
