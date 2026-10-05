---
title: "Publish signed builds to GitHub Releases for Obtainium"
status: backlog
area: distribution
depends_on: []
---

Every install so far has been `adb install` from a workstation, which does not
scale past one phone and gives no way to notice that an update exists.

CI publishes a signed APK to a GitHub release and Obtainium on the phone watches
the repository and offers the update. It needs no server work, and it makes the
app installable by anyone who wants it without anything being pushed on them -
they point Obtainium at the repository or they do not.

The workflow should call the two validation workflows rather than repeat them,
the way "Ingest - build" already calls "Ingest - validation" so that an image is
only built once its checks have passed. Both already expose `workflow_call` for
it. Calling "Android - migration tests" matters most: a release is the last
point at which an unusual failure can be caught before it reaches a phone, and
its post-merge job is where the slower devices - API 28, the level the app
targets, and API 33 and 37 - run, which costs a release nothing because nobody
is waiting on one the way they wait on a pull request.

One prerequisite regardless: the release build type has no signing configuration
and everything installed so far is debug-signed. Moving to a release key means
the first such install cannot upgrade what is there and has to replace it, which
deletes the database - so the backlog has to be uploaded before that switch, not
after. The keystore then lives as a CI secret, and losing it means no existing
install can ever be upgraded again.

Debug builds have their own id now, `com.anonymus09.carsensors.debug`, so they
install beside the app rather than over it. The phones that already have it
therefore hold a debug-signed build under the release id, and that install is
the one the switch replaces - nothing a debug build does will update or remove
it in the meantime.

Worth noting that staying off Play is what keeps `targetSdk = 28` tenable at
all, since Play enforces a minimum target version and nothing else does. That is
an argument for this route rather than a consequence of it.
