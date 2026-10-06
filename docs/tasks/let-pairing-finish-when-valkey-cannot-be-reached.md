---
title: "Let pairing finish when Valkey cannot be reached"
status: next
area: device-auth
depends_on: []
---

With `REDIS_URL` set and Valkey down, pairing a phone never finishes: the
request waits indefinitely and the pairing dialog never shows the new token.
Found when the end-to-end test that pairs a replacement phone failed on a
machine whose root `.env` sets `REDIS_URL`, as `.env.example` does, with Valkey
stopped. The same happens in production whenever Valkey is.

Rotating a credential calls `forgetDeviceCredential` in
[`device-credential-cache.ts`](../../www/src/lib/server/device-credential-cache.ts)
to clear `ingest`'s cached copy. It means to fail soft: it catches the error
and returns `false`, so the person pairing can be told the old token may work
for a few more minutes. But that error never arrives. It awaits
`client.connect()`, and node-redis by default retries a refused connection
forever -
[exponential backoff to at most two seconds, plus jitter](https://github.com/redis/node-redis/blob/master/docs/client-configuration.md#reconnect-strategy) -
so `connect()` only settles once Valkey is back.

That breaks the rule the architecture sets for a service call: acceptable only
where the caller can carry on without it. Giving this client a bounded
`reconnectStrategy` - a few attempts, then an `Error` - makes `connect()`
reject, the `catch` return `false`, and pairing finish with its warning. A
client that has given up has to be replaced on the next use rather than reused,
which is what `connectedClient` does today. An end-to-end test with `REDIS_URL`
pointing at nothing would keep it that way.

Live tracking in
[`live-tracking.ts`](../../www/src/lib/server/live-tracking.ts) connects the
same way, but there waiting may be the right behaviour: its `ready` promise
settles once Valkey returns, and until then a live vehicle stream yields
nothing while the map keeps its periodic poll. Worth deciding on purpose while
here - whether a stream should rather end at once, as it does when `REDIS_URL`
is unset - not changing by accident.
