---
title: "Let a vehicle be retired from the web application"
status: backlog
area: device-auth
depends_on: []
---

Two thirds of retiring a vehicle exists and the third is missing, which is the
part an operator would actually touch. `ingest` answers a deactivated row with
403 and `error="insufficient_scope"`, and the phone now says so plainly and
offers to discard a backlog that will never be accepted. Nothing in `www` sets
`is_active` to false - it is written `true` on insert and read in the summary
query, and that is the whole of it. Retiring a vehicle today means a hand-written
`UPDATE`.

What it needs is small: an action on the vehicle, a confirmation that says what
it costs, and the reverse for bringing one back. Worth deciding at the same time
whether a deactivated vehicle disappears from the list or shows greyed out with
its history intact - `getVehicleSummaries` filters on `is_active = TRUE`, so
today it would simply vanish along with every sample it ever sent, which is
probably not what retiring a car should mean.

**It must clear the credential cache, exactly as rotation does.** `ingest`
caches a device's credential under `device_credential:{device_id}` for
`KNOWN_DEVICE_CACHE_TTL_SECS`, and that entry carries `is_active` with it, so a
row deactivated in the database goes on being accepted until the cache lapses.
This is not theoretical: it happened while testing the phone's handling of a
retired device, where the 403 only appeared after the key was deleted by hand.
`rotateDeviceToken` already calls `forgetDeviceCredential` and reports whether
it succeeded; deactivating and re-activating both want the same call, and
re-activating wants it so a vehicle brought back starts uploading at once rather
than after the window.
