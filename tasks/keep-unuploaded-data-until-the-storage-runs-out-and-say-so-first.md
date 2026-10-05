---
title: "Keep unuploaded data until the storage runs out, and say so first"
status: backlog
area: android
depends_on: []
---

`deleteUploadedOlderThan` bounds only the rows that have been uploaded. Nothing
bounds the rest, and the rest is the part that matters: the `/api` bug alone
built up 22,866 rows, and a month of driving with no reachable server would be
far larger. A phone that fills its storage stops being a logger.

`deleteNotUploaded` exists but is not a bound. It runs only when somebody
answers the pairing prompt by discarding what was recorded before an identity
existed, which is a deliberate choice made once - nothing calls it on the
phone's own initiative, and nothing should until there is a policy to call it
under.

The policy that follows from what the data is worth. Postgres is the record once
a row has arrived there, so an uploaded row has no reason to stay on the phone
at all and can go promptly rather than after seven days. An unuploaded row is
the only copy in existence and should survive as long as there is room for it.
Only under real storage pressure should any be dropped, and then the oldest
first, because the recent ones describe where the vehicle is now.

Deleting the only copy of something should never be the first the user hears of
it. `UploadSilenceNotifier` already warns once telemetry has gone unsent for
`UPLOAD_SILENCE_WARNING_MS`, which covers the ordinary case of a server that
has stopped accepting anything. What a storage ceiling adds is a second, more
insistent warning as the backlog approaches it, because a phone that is about
to start discarding rows deserves more notice than one that is merely behind.
That one belongs here, with the threshold it is measured against.

Worth knowing about the existing warning when this is written: it is raised
from the foreground service's throttled backlog check, so it is only evaluated
while the logger is actually recording. A phone that is driven regularly hears
within a day; one that is parked for a month hears nothing until it next
records. That is the right trade for a warning about data still being
collected, but a warning about storage pressure may not want to inherit it.

**Related.** The warning lives in the foreground service's backlog check, which
[Split the foreground service up](split-the-foreground-service-up.md) would
move.
