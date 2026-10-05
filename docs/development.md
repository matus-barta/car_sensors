---
title: "Development setup"
---

How to set up a machine to work on the project and check each piece the way CI does. The tools it needs are listed under Dev Requirements in the [root README](../README.md#dev-requirements).

## Setting up

Install the SQLx CLI, with PostgreSQL and Rustls support:

```bash
cargo install sqlx-cli \
    --no-default-features \
    --features rustls,postgres
```

Install the web application's dependencies:

```bash
cd www && pnpm install
```

Create the environment file at the repository root - see [Environment](#environment) for what goes in it:

```bash
cp .env.example .env
```

Start the local infrastructure - Postgres, Valkey and pgAdmin:

```bash
cd tools && docker compose up -d
```

## Environment

There is one environment file, `.env` at the repository root. `ingest`, `www` and the SQLx CLI all find it there; the Android app has none.

`ingest` reads `DATABASE_URL`, `REDIS_URL`, `SERVER_IP_PORT`.

`www` requires `DATABASE_URL`, `ORIGIN` and `BETTER_AUTH_SECRET`; each is validated at startup and throws if missing. The rest are optional. `PUBLIC_OSM_VECTOR_TILE_URL` and `PUBLIC_OSM_STYLE_URL` fall back to the public OpenStreetMap tiles and the VersaTiles style. `REDIS_URL` enables live tracking of the selected vehicle, and lets a token rotation clear `ingest`'s credential cache at once. `TRUSTED_PROXIES` lists the proxies Better Auth skips over in `X-Forwarded-For` to find the client address it rate-limits sign-in by.

`www/.env.test` holds E2E-only values and is committed on purpose; the Playwright config loads it and passes it to the preview server, because `vite preview` runs in production mode and would not read it otherwise.

## Checking each piece

Each piece is checked the same way locally as it is in CI, so a green run here
means a green run there.

Rust, from the repository root:

```bash
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

The `ingest` integration tests run only when `TEST_DATABASE_URL` and
`TEST_REDIS_URL` are set, and skip their assertions otherwise - with the
variables set and nothing listening, they fail. `cd tools && docker compose up
-d` starts Postgres and Valkey; the tests want a database of their own, created
once:

```bash
docker compose -f tools/docker-compose.yml exec postgres createdb -U postgres ingest_test
TEST_DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/ingest_test \
  TEST_REDIS_URL=redis://127.0.0.1:6379 cargo test -p ingest
```

Web application:

```bash
cd www
pnpm check    # svelte-kit sync and svelte-check
pnpm lint     # prettier and eslint
pnpm test     # unit, component and end-to-end
```

Android application:

```bash
cd android
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest
```

That is formatting, static analysis, Android lint and the unit tests, which run
on the JVM and need no device - and what Android Studio's shared run
configuration **Verify (lint + tests)** runs, beside **Format (ktlint)**. The
instrumented tests do need a device:

```bash
./gradlew connectedDebugAndroidTest        # a handset over adb
./gradlew api30atdDebugAndroidTest         # or a Gradle-managed emulator
```

See [`docs/android-app.md`](android-app.md) for both.

Documentation, from the repository root:

```bash
lychee './**/*.md' .claude/CLAUDE.md   # links between files, settings in lychee.toml
actionlint                             # the workflows in .github/
```

The documentation site, built from the same Markdown:

```bash
cd docs/starlight
pnpm install
pnpm dev       # a local preview that reloads as pages change
pnpm check     # type-checks the site's TypeScript
pnpm build     # what CI runs; fails on a link between pages that does not resolve
```
