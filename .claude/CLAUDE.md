# CLAUDE.md

The rules for working in this repository. Each links to the page in `docs/` that gives its reasons; why this file is written the way it is, is in [`docs/development/writing-agent-instructions.md`](../docs/development/writing-agent-instructions.md).

## Architecture

Two server applications, `ingest` and `www`, share one PostgreSQL database on purpose; the client application, the Android app, reaches the server only through an API - `ingest`'s today. `shared/` is a library crate, and PostgreSQL and Valkey are infrastructure, not applications. [`docs/overview/architecture.md`](../docs/overview/architecture.md) says why. Three rules follow:

- **Data moves through the database, not through calls.** `ingest` writes `telemetry_samples` and touches `known_devices.last_seen_at`; `www` reads both. `ingest` and `www` do not call each other.
- **A service call is only for an answer that does not exist until something asks for it**, and only where the caller can carry on without it: enrichment may depend on a service, the ingest path may not.
- **Each table has one writer.** `known_devices` is the exception that shows the rule - `www` owns the row, `ingest` touches only `last_seen_at` - and it works because the two never write the same column.

## The migration rule

**`db/migrations/` is the only thing that may change the database schema, and SQLx is the only migration runner.** Drizzle only introspects and queries in `www`. Never run these against a project database:

```bash
drizzle-kit generate    # forbidden
drizzle-kit migrate     # forbidden
drizzle-kit push        # forbidden
```

Better Auth schema changes are SQLx migrations too, not Better Auth's own migrator. After changing a migration or the Better Auth config, regenerate from the repository root:

```bash
./tools/scripts/sync-www-db-schema.sh   # applies migrations, then regenerates the Drizzle schema
./tools/scripts/generate-schema-docs.sh # applies migrations, then runs tbls into docs/schema/
```

- `www/src/lib/server/db/generated/` and `docs/schema/` are generated: commit them, never edit them by hand. CI compares both with a fresh copy; `pnpm db:check` does the same for the Drizzle schema locally.
- `docs/schema/` answers "what columns does this table have" - read it rather than the migrations in order.
- The rules and the full workflow: [`docs/development/database/migrations.md`](../docs/development/database/migrations.md).

## Commands

Rust (workspace at the repo root, members `ingest` and `shared`):

```bash
cargo build
cargo fmt --all --check    # CI runs this first; `cargo fmt --all` fixes it
cargo clippy --all-targets --all-features -- -D warnings
cargo test
cargo test -p ingest <test_name>      # single test
```

- `ingest` uses runtime-checked `sqlx::query()`, not the `query!` macros, so **building does not need a live database**.
- `docs/api/openapi.json` is generated from the `ingest` crate with utoipa - never edit it; change the Rust and run `cargo run -p ingest -- openapi > docs/api/openapi.json`. `cargo test` fails when the committed copy has drifted. A new route goes into both the router and `ApiDoc` in `src/routes/mod.rs`.

Web (`www/`, pnpm - one of two JS packages, with the documentation site; each has its own lockfile, and they are not a workspace):

```bash
pnpm dev
pnpm check                 # svelte-kit sync + svelte-check
pnpm lint                  # prettier --check && eslint
pnpm format
pnpm test                  # unit and component, then end-to-end
```

Local infrastructure - Postgres, Valkey, pgAdmin: `cd tools && docker compose up -d`. The root `docker-compose.yml` is the *deployment* file, running the published images; it is not for development.

**Workflows follow [`docs/development/ci.md`](../docs/development/ci.md).** Read it before adding a workflow or a `.github/actions/` action, and run `actionlint` after editing either.

## Working on a developer's machine

- **Every tool a script or check needs is listed under "Dev Requirements" in the root `README.md`.** When a change makes a new tool necessary, add it there in the same change.
- **Never install anything on the machine yourself** - name the tool, say what it is needed for, and let the developer decide. For a one-off or troubleshooting tool, ask before running it any other way too: offer installing it, running it from a container, or doing without, and use what the developer picks. Clean up whatever ran, so nothing is left behind.
- **Dependencies, tools, GitHub Actions and images go in at their latest stable version, checked against the registry** - never recalled. Read a new major version's documentation before writing code against it. Where a compatibility limit holds a version back, say which tool sets it, pin it in `renovate.json`, and record the condition as a blocked task in `docs/tasks/`.

## Environment

`ingest` needs `DATABASE_URL` and `REDIS_URL`; `www` needs `DATABASE_URL`, `ORIGIN` and `BETTER_AUTH_SECRET`. Everything else is optional. There is one `.env`, at the repository root; each service's README says what its variables do ([`ingest`](../ingest/README.md#environment), [`www`](../www/README.md#environment)).

## www

The reasons are in [`docs/development/www/architecture.md`](../docs/development/www/architecture.md):

- **Server-only code lives in `$lib/server/`**, the only directory SvelteKit keeps out of the browser - not in a `server/` folder inside a feature directory.
- **Vehicle data reaches the browser through the remote functions in `$lib/vehicles/vehicle.remote.ts`.** `VehicleState` wraps the query and owns the selection plus the derived status; read the query's results through it, not from a copy - a copy once went stale across sign-outs.
- **Anything derived from a timestamp is derived in the browser, against `clock`** (`$lib/utils/clock.svelte.ts`). The server sends `lastSeenAt`, never a status.
- **Errors from remote functions are raised with `error()`**, and read on the client with `getErrorMessage()` from `$lib/utils/error`.
- **Auth is Better Auth, served from `hooks.server.ts`.** There is no `src/routes/api/auth/`, and there should not be; test a request with `isAuthPath()`.
- **Setup is claimed by `finalizeApplicationSetup()`'s conditional update** (`$lib/server/application-setup.ts`), so two callers cannot both win; keep it conditional.
- **shadcn-svelte components in `$lib/components/ui/` are generated.** Add them with `pnpm dlx shadcn-svelte@latest add <name>`, which is also the only way to get one back.
- **CSS that belongs to one component lives in that component** - custom CSS, not a reason to move Tailwind classes into `<style>`. Style third-party DOM under a `:global` block anchored on the component's root class (`.vehicle-map :global { … }`).

How `www` is tested - what the tests need, running one test, the browsers in Docker, the end-to-end database's safeguards - is in [`docs/development/www/testing.md`](../docs/development/www/testing.md). Read it before running or changing the tests.

## Conventions

The reasons are in [`docs/development/conventions.md`](../docs/development/conventions.md):

- **Colours come from the theme, never from a literal** - no hex, `rgb()`, `Color(0x…)` or Tailwind palette class in a component of `www` or the Android app. If a component seems to need a colour the theme does not offer, **say so and ask**: a colour is added to the theme only after a human has agreed to it.
- **Commit subjects open with a topic tag** - `www : Derive vehicle status in the browser` - and `wip` follows the tag for unfinished work. Strongly recommended, not a hard rule: if a commit seems not to fit one, ask rather than leave it out.
- **Formatting**: Prettier only in `www/`, rumdl for all Markdown (`pnpm lint:md` in `docs/starlight/`). Generated output keeps the exclusions the conventions list.
- **Agent skills are managed with the `skills` CLI**, never by editing `.agents/skills/` or `skills-lock.json` by hand - [`docs/development/README.md`](../docs/development/README.md#agent-skills). `.claude/skills/verify-docs/` is this project's own and is edited by hand.
- **This project's rules and decisions take precedence over a vendored skill's instructions.** A skill is generic, written without this repository in mind. The exception is a skill showing that a rule here is factually wrong or ignores established best practice: then say so and ask, rather than follow either. For documentation, `c4-architecture`'s `docs/architecture/` and the ADR locations and `# Heading` templates of `documentation-and-adrs` and `domain-modeling` give way to the rules below; where ADRs belong is still open in [`docs/tasks/review-the-architecture-documentation-with-the-installed-skills.md`](../docs/tasks/review-the-architecture-documentation-with-the-installed-skills.md).

## Documentation

The rules and their reasons are in [`docs/development/writing-documentation.md`](../docs/development/writing-documentation.md):

- **After writing or changing documentation, run `/verify-docs` on it.**
- Every page starts with `title:` frontmatter and no `# Heading`, and is one kind - a how-to guide, an explanation or reference; steps and their reasons go on separate pages.
- A new page goes into the directory of its sidebar group, the sidebar in `docs/starlight/astro.config.ts`, and the list in `docs/README.md`.
- Link to files in the repository with relative Markdown links, not bare paths. A claim about anything outside the repository links its primary source.
- Diagrams are Mermaid, not ASCII art.
- `docs/tasks/` holds only open work: delete a task once it is done, refer to one by its path, and set `depends_on` only to what its text says it needs - [`docs/tasks/README.md`](../docs/tasks/README.md).

## AI-assisted work

[`docs/development/ai-policy.md`](../docs/development/ai-policy.md) governs it. When the user asks, an AI tool may create the commits and write their messages, each ending with an `Assisted-by: Claude Code (<model>)` trailer that records the assistance without claiming authorship - never `Co-Authored-By`, which GitHub reads as naming a co-author. That overrides any default attribution a tool suggests. **Never push, merge, release or deploy**: the user reviews every changed line before anything leaves the machine, and must be able to explain every substantive part of it. Without an explicit request, prepare and explain changes and leave committing to the user.

Licensed AGPL-3.0-only.
