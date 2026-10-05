---
title: "Make the schema check name the database it checked"
status: backlog
area: ci
depends_on: []
---

`pnpm db:check` regenerates the Drizzle schema and fails if it differs from what
is committed, which sounds like it proves the committed schema matches the
database the application uses. It does not. `drizzle.config.ts` reads
`process.env.DATABASE_URL`, so the check runs against whatever the shell
exports, while the running application reads `www/.env` - and those can be two
different databases without either complaining.

This is not hypothetical. The device token migration was applied with
`DATABASE_URL` exported for one database, so the schema was regenerated from
that one and `pnpm db:check` passed, while `www` went on talking to another that
had never seen the migration and answered every insert with
`column "token_hash" of relation "known_devices" does not exist`. Everything
that could have caught it - the check, the unit tests, the end-to-end suite
against its own throwaway database - was looking somewhere else.

One of the three cheap fixes has since landed on its own. There is now a single
environment file at the repository root, and both halves of the script default
to it without being told: `sqlx migrate run` finds it by walking up, and
`drizzle.config.ts` loads it explicitly. The two ends can no longer be pointed
at different databases by an exported variable, which is what actually went
wrong.

Two are left, and the second is the one that catches the general case. Have
`sync-www-db-schema.sh` print the database it is about to migrate and generate
from, since a mismatch is obvious the moment it is named out loud. And consider
a startup check in `www` comparing the applied migration list against the
migrations on disk, so a database that is behind says so once at boot rather
than once per failed query - that catches a correct environment pointing at an
unmigrated database, which fails in exactly the same way and which the shared
file does nothing about.
