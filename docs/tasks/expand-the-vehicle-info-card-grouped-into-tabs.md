---
title: "Expand the vehicle info card, grouped into tabs"
status: backlog
area: www
depends_on: []
---

`vehicle-info-card.svelte` shows name, id, status, last seen, coordinates,
bearing and - when it lags the last contact - how old the position is. That is
a small slice of what is already being collected. A telemetry row persists the
full sensor suite (power and charging state, GPS speed, altitude, accuracy and
provider, plus accelerometer, gyroscope, magnetometer and barometer readings -
see `telemetry_samples` in `20260614151604_init_telemetry.sql`), and
`LiveSample` already carries speed, altitude, accuracy, charging and power
source over the live stream. None of it reaches the card, because
`VehicleSummary` carries only lastSeenAt, positionAt, latitude, longitude and
bearing, and `VehicleLivePosition` the same minus positionAt.

Worth growing the card to show more of this, and grouping it into tabs rather
than one long stacked list once it does - an "Overview" tab for identity,
status and location, a "Telemetry" or "Sensors" tab for the rest. `Tabs` is
not vendored yet (`pnpm dlx shadcn-svelte@latest add tabs`), so this would be
the first shadcn-svelte component added since the ones already in
`src/lib/components/ui/`.

Two things to carry over deliberately rather than lose along the way: the card
is absolutely positioned over the map at a width capped by the viewport
(`w-[min(24rem,calc(100vw-2rem))]`), so it needs to keep behaving on narrow
screens once there is more inside it; and surfacing more live fields means
growing `VehicleLivePosition` and the merge in `vehicle-state.svelte.ts`
beyond the four fields it carries now.

**Related.** [Show on the vehicle card what the phone is not allowed to
do](show-on-the-vehicle-card-what-the-phone-is-not-allowed-to-do.md) grows the
same card; the tabs decide where it goes.
