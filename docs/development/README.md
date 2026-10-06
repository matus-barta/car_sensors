---
title: "Development setup"
---

How to set up a machine to work on the project and check each piece the way CI does. The tools it needs are listed under Dev Requirements in the [root README](../../README.md#dev-requirements).

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

Each service's README lists the variables it reads, which it requires and what the rest do: [`ingest`](../../ingest/README.md#environment) and [`www`](../../www/README.md#environment). A deployment sets them on the containers instead - see [`docs/deployment/README.md`](../deployment/README.md).

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

The `ingest` integration tests need the local infrastructure running, a
database of their own and two variables - the [`ingest` README](../../ingest/README.md#development)
says why:

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

The tests' browsers run in Playwright's own Docker image, so Docker has to be
running; the end-to-end tests also need the local infrastructure.
[`www/scripts/with-browsers.ts`](../../www/scripts/with-browsers.ts) starts the
image matching the installed Playwright and leaves it running for the next
run - `pnpm test:browsers:stop` removes it. The tests themselves run on this
machine and connect to it, the same way as in CI. A browser in a container
cannot open a window, so for `--headed`, `--debug` or Vitest's visible
browser, set `PLAYWRIGHT_LOCAL_BROWSERS=1` to use browsers installed here
instead (`pnpm exec playwright install chromium`).

Android application:

```bash
cd android
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest
```

The instrumented tests need a device:

```bash
./gradlew connectedDebugAndroidTest        # a handset over adb
./gradlew api30atdDebugAndroidTest         # or a Gradle-managed emulator
```

What each of these checks, and the Android Studio run configurations that do the
same, are in [`docs/development/android/README.md`](android/README.md#checking-the-code).

Documentation, from the repository root:

```bash
lychee './**/*.md' .claude/CLAUDE.md   # links between files, settings in lychee.toml
actionlint                             # the workflows in .github/
```

Markdown style and the documentation site, from `docs/starlight/` - its
dependencies include [rumdl](https://rumdl.dev/), the Markdown linter, which
checks every Markdown file in the repository with the rules in
[`.rumdl.toml`](../../.rumdl.toml):

```bash
cd docs/starlight
pnpm install
pnpm lint:md     # every Markdown file in the repository, as CI checks it
pnpm format:md   # fixes what can be fixed automatically
pnpm dev         # a local preview that reloads as pages change
pnpm check       # type-checks the site's TypeScript
pnpm build       # what CI runs; fails on a link between pages that does not resolve
```
