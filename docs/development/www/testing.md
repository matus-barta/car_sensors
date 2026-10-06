---
title: "Testing www"
---

How to run the web application's tests, and what each kind needs. They run the same way on a developer's machine and in CI.

## What they need

- **Docker, running.** The tests' browsers run in Playwright's own image - see [the browsers](#the-browsers).
- **For the end-to-end tests, the local infrastructure**, started with `cd tools && docker compose up -d`: Postgres, and Valkey too whenever the root `.env` sets `REDIS_URL`, as [`.env.example`](../../../.env.example) does - pairing a phone clears a cached credential there.
- **For the end-to-end tests, the SQLx CLI on `PATH`.** It applies the migrations to the test database; [development setup](../README.md#setting-up) installs it.

## Running them

From `www/`:

```bash
pnpm test                                   # unit and component, then end-to-end
pnpm test:unit                              # unit and component, watching for changes
pnpm test:unit --run                        # the same, once
pnpm test:unit --run <file>                 # one file
pnpm test:e2e                               # end-to-end
pnpm test:e2e -g "adds a vehicle"           # one end-to-end test, by name
```

## The browsers

[`www/scripts/with-browsers.ts`](../../../www/scripts/with-browsers.ts), which both test commands go through, starts `playwright run-server` in the Playwright image matching the installed Playwright, and the tests connect to it. Only the browser runs in the container: the tests, the preview server, the SQLx CLI and Postgres all run on this machine, and the browser's `localhost` is routed back here. It is the setup [Vitest documents](https://vitest.dev/config/browser/playwright) for running browsers in Docker.

The container is left running for the next run, so the image is pulled once per Playwright version; `pnpm test:browsers:stop` removes it.

A browser in a container cannot open a window. For `--headed`, `--debug` or Vitest's visible browser, set `PLAYWRIGHT_LOCAL_BROWSERS=1` to use browsers installed on this machine instead, after `pnpm exec playwright install chromium`.

## Unit and component tests

Vitest runs them as two projects:

- `client` - in the browser, for `src/**/*.svelte.{test,spec}.{js,ts}`, except under `src/lib/server/`.
- `server` - in Node, for every other `src/**/*.{test,spec}.{js,ts}`.

`expect.requireAssertions` is on, so a test that asserts nothing fails.

## End-to-end tests

They run one at a time against a real Postgres database, created and migrated when the run starts and dropped when it ends. Each test truncates it and seeds what it needs.

The database's name, from `DATABASE_URL`, must end in `_test`, and `POSTGRES_ADMIN_URL`, used to create and drop it, must point at a different database. The fixture refuses to run otherwise, so a misconfigured run cannot empty a database that matters.

`www/.env.test` holds the end-to-end values and is committed on purpose. The Playwright config loads it and passes it to the preview server, because `vite preview` runs in production mode and would not read it otherwise.
