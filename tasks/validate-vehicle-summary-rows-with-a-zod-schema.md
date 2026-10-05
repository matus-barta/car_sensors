---
title: "Validate vehicle summary rows with a zod schema"
status: backlog
area: www
depends_on: []
---

`getVehicleSummaries()` in `vehicle-service.ts` reads a raw SQL join and casts
the driver's rows to `VehicleSummaryRow` by assertion, then defends the
numeric fields with three hand-rolled `normalizeLatitude`, `normalizeLongitude`
and `normalizeBearing` functions that each repeat the same "finite and in
range, otherwise null" shape. `parseLiveSample` in `live-tracking.ts` does the
equivalent job with a zod schema instead, for data arriving from Valkey rather
than Postgres - the two would read the same way if the SQL row were validated
the same way.

This is not a correctness fix - the normalizers already reject the same bad
values a schema would - so it is worth doing for consistency, one validation
approach for data that crosses a boundary rather than two, not because
anything is broken today.
