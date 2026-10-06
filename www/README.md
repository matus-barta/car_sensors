# Car Sensors Web

SvelteKit web application for the Car Sensors platform.

The application provides:

- User authentication with Better Auth
- Initial administrator setup
- Vehicle management
- Vehicle location and telemetry visualization
- Type-safe PostgreSQL access through Drizzle ORM
- shadcn-svelte user interface components

## Requirements

The tools under [Dev Requirements](../README.md#dev-requirements) in the root
README. Node.js has to be 24 or newer - `engines` in `package.json` says so, and
pnpm [refuses to install](https://pnpm.io/settings/cli#enginestrict) a project
whose own `engines` the running Node.js does not meet - and pnpm the version
`packageManager` pins.

## Setup

```bash
pnpm install
```

The environment file is the one at the repository root - see
[`docs/development/README.md`](../docs/development/README.md#setting-up) - and this directory
deliberately has none: `kit.env.dir` in `vite.config.ts`, the Drizzle config and
the Better Auth script are all pointed at the root, so a `www/.env` would simply
not be read. Two files drifted apart once and left this application talking to a
different database from the service writing to it, with every check passing
against the other one.

## Environment

| Variable                                             | Required |                                                                                                                                                                                                                   |
| ---------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DATABASE_URL`                                       | yes      | PostgreSQL connection string                                                                                                                                                                                      |
| `ORIGIN`                                             | yes      | The URL the application is reached at; Better Auth [rejects requests from any other](https://www.better-auth.com/docs/reference/security#trusted-origins)                                                         |
| `BETTER_AUTH_SECRET`                                 | yes      | The signing key - `openssl rand -base64 32` generates one                                                                                                                                                         |
| `REDIS_URL`                                          | no       | The Valkey `ingest` uses. Enables live tracking of the selected vehicle, and lets a token rotation clear `ingest`'s credential cache at once; without it the vehicle list still updates through its periodic poll |
| `PUBLIC_OSM_VECTOR_TILE_URL`, `PUBLIC_OSM_STYLE_URL` | no       | A tile server of your own, in place of the public OpenStreetMap tiles and the VersaTiles "colorful" style                                                                                                         |
| `TRUSTED_PROXIES`                                    | no       | The proxies Better Auth skips over in `X-Forwarded-For` to find the client address it rate-limits sign-in by - see [`docs/deployment/README.md`](../docs/deployment/README.md#what-the-compose-file-runs)                       |

The three required ones are checked at startup, which throws without them. The
end-to-end suite has its own committed `.env.test` instead.

## Development

Start the development server:

```bash
pnpm dev
```

The application is available at:

```text
http://localhost:5173
```

On a new installation with no users, the application redirects to `/auth/setup` and asks for the initial administrator account.

Public registration is disabled: the setup screen creates the first account. There is no way to add further accounts from the application yet - Better Auth's admin plugin is enabled, but nothing uses it; see [`docs/tasks/manage-accounts-from-the-web-application.md`](../docs/tasks/manage-accounts-from-the-web-application.md).

How to check it the way CI does is in [`docs/development/README.md`](../docs/development/README.md#checking-each-piece).

## Database schema

The schema belongs to the SQLx migrations in [`db/migrations/`](../db/migrations/);
Drizzle only reads it, and never creates or applies a migration. The generated
schemas in `src/lib/server/db/generated/` are committed and never edited by
hand. After changing a migration or the Better Auth configuration, run
`./tools/scripts/sync-www-db-schema.sh` from the repository root.
[`docs/development/database/migrations.md`](../docs/development/database/migrations.md) has the rules and
the whole workflow.

## Authentication

Authentication is implemented with Better Auth and its Drizzle adapter.

Relevant files:

```text
src/lib/server/auth.ts
src/lib/server/auth-bootstrap.ts
src/lib/server/application-setup.ts
src/routes/auth/login/
src/routes/auth/setup/
src/routes/auth/sign-out/
```

The first account created during setup receives the `admin` role.

Better Auth tables are generated separately from the application schema because Better Auth requires JavaScript `Date` values for its timestamp fields.

## Project structure

```text
src/lib/components/          Application components
src/lib/components/ui/       Generated shadcn-svelte components
src/lib/map/                 MapLibre style construction
src/lib/server/              Server-only code, unreachable from the browser
src/lib/server/db/generated/ Generated Drizzle schemas
src/lib/utils/               Framework-agnostic helpers
src/lib/vehicles/            Vehicle domain: types, status, state, remote functions
src/routes/                  Application routes
src/routes/auth/             Authentication routes
static/                      Static assets
e2e/                         Playwright end-to-end tests
```

Why the code is laid out this way - server-only code, the remote functions,
the generated components - is in
[`docs/development/www/architecture.md`](../docs/development/www/architecture.md).

## Useful commands

```bash
pnpm dev                   # Start development server
pnpm check                 # Run SvelteKit and TypeScript checks
pnpm lint                  # Run Prettier and ESLint checks
pnpm format                # Format the project
pnpm test                  # Run unit and end-to-end tests
pnpm build                 # Create production build
pnpm preview               # Preview production build
pnpm db:sync               # Regenerate database schemas
pnpm db:check              # Verify generated schemas are current
pnpm db:studio             # Open Drizzle Studio
pnpm auth:schema:generate  # Regenerate the Better Auth schema
```

## License

This project is licensed under the GNU Affero General Public License version 3 only:

```text
AGPL-3.0-only
```

See the root `LICENSE` file for the complete license terms.
