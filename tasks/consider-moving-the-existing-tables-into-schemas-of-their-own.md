---
title: "Consider moving the existing tables into schemas of their own"
status: backlog
area: database
depends_on: []
---

Everything except the geocoder's cache lives in `public`. Nothing is wrong with
that today, and this is worth recording as an option rather than a fault to fix:
the value is in what it prevents later, not in anything it repairs now.

Postgres schemas are namespaces rather than walls, which is exactly why they
suit an arrangement of small services over one database. Joins, foreign keys and
transactions all work across them unchanged - it is still one database and the
planner does not care - so none of what makes a shared database pleasant is
given up. What is gained is a name for each boundary and, if it is wanted,
enforcement: privileges are granted per schema, so with each service connecting
as its own role, who may read what stops being a convention that has to be
remembered.

Naming them after domains rather than services would age better, since services
get renamed and split while domains do not:

| Schema      | Holds                     | Written by    |
| ----------- | ------------------------- | ------------- |
| `auth`      | the Better Auth tables    | `www`         |
| `fleet`     | devices, ownership        | `www`         |
| `telemetry` | samples                   | `ingest`      |
| `trips`     | trips                     | trip service  |
| `geocoder`  | the place cache           | geocoder      |

That also puts the one-writer rule into the structure instead of leaving it in
prose.

If users are ever associated with vehicles rather than only with sessions, the
relationship belongs on the domain side - `fleet.devices.owner_id` referencing
`auth."user"`, or a join table in `fleet` if it becomes many to many - so that
the dependency runs one way. The domain may reference `auth`; `auth` should know
nothing about vehicles, which keeps it a leaf that could be replaced without
disturbing anything pointing out of it. The friction to expect is that a foreign
key into Better Auth's `user` table makes a later upgrade that alters or
recreates it a coordinated change rather than a local one. Those migrations are
already written by hand here rather than by Better Auth's own migrator, so the
timing is under control, but it stops being free.

The move itself is cheap - `ALTER TABLE ... SET SCHEMA` is metadata only, with
no rewrite - but it is not free downstream. The generated Drizzle schema has to
be regenerated through `./tools/scripts/sync-www-db-schema.sh`, `pnpm db:check`
fails on drift, and any raw SQL in `www` needs its names qualified, so the
migration and the generated output have to land together. Drizzle itself copes
perfectly well by way of `pgSchema`.

One honest trade. Schemas make a future split into separate databases easier,
because moving `geocoder.*` elsewhere is far simpler than extracting it from a
shared `public`. A foreign key across schemas is precisely what would have to be
broken to do that. Referential integrity and splittability pull against each
other here, and at this size integrity is the better buy - but it is a choice
rather than a free lunch.
