---
title: "Stream uploads as NDJSON instead of one JSON array"
status: backlog
area: protocol
depends_on: []
---

An upload is a single JSON array, and the `Json` extractor buffers the whole
body before parsing it, so the memory a request costs is bounded only by the
body limits in `ingest/src/lib.rs` - currently 4 MiB compressed and 32 MiB once
expanded. That is the reason a limit has to exist at all, and it caps how much
backlog a device can hand over in one request.

Sending one sample per line instead would let the service parse and insert as
the body arrives, making the cost of a request proportional to a single sample
rather than to the whole backlog, and removing the need to guess a limit.

This is a protocol change on both sides: the Android uploader would have to emit
NDJSON as well, so it belongs with the Android work rather than as a
server-only change.
