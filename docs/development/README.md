---
title: "Development setup"
---

How to set up a machine to work on the project, check each piece the way CI does, and manage the agent skills. The tools it needs are listed under Dev Requirements in the [root README](../../README.md#dev-requirements).

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

`www/.env.test` holds the end-to-end tests' own values, committed on purpose - [Testing www](www/testing.md#end-to-end-tests) says why.

## Checking each piece

Each piece is checked the same way locally as it is in CI, so a green run here
means a green run there.

Rust, from the repository root:

```bash
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
cargo test -p ingest <name>   # one test, by name
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

What the tests need - Docker for their browsers, and the local infrastructure
for the end-to-end ones - and how to run one at a time are in
[Testing www](www/testing.md).

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

## Agent skills

The skills AI agents use here are vendored in `.agents/skills/` and linked into
`.claude/skills/`, where Claude Code looks for them; [`skills-lock.json`](../../skills-lock.json)
records where each came from. All three are committed. They are managed with the
`skills` CLI rather than edited by hand:

```bash
pnpm dlx skills list                              # what is installed
pnpm dlx skills add <owner>/<repo> -s <skill> -y  # vendor one
pnpm dlx skills update -p -y                      # update every one
pnpm dlx skills remove <skill> -y                 # its directory, link and lock entry together
```

An update skips a skill whose source repository holds the same name at more
than one path, rather than guess which to follow. Adding it again from its
exact path - `pnpm dlx skills add https://github.com/<owner>/<repo>/tree/main/<path> -y` -
updates it and records that path from then on.

A skill runs with the agent's full permissions, so read what an added or
updated one says before committing it. Their Markdown is left as its authors
wrote it: rumdl skips `.agents/` and `.claude/`, and Prettier runs only in `www/`.

One skill is this project's own: `.claude/skills/verify-docs/` is a real
directory rather than a link, edited by hand - see
[writing documentation](writing-documentation.md#after-writing).
