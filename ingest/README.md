# ingest

The service devices upload telemetry to. Rust, axum, writing to PostgreSQL and announcing live positions through Valkey.

It has no user interface and no relationship with `www` beyond the database they share. See [`../docs/overview/architecture.md`](../docs/overview/architecture.md) for how that fits together.

## Environment

| Variable | Required | Default | |
| -------- | -------- | ------- | - |
| `DATABASE_URL` | yes | | PostgreSQL connection string |
| `REDIS_URL` | yes | | Valkey connection string |
| `SERVER_IP_PORT` | no | `0.0.0.0:3000` | What to bind to |

The first two are fatal if missing rather than defaulted, because a silent fallback to a local database is a worse failure than not starting.

## API

The contract is [`docs/api/openapi.json`](../docs/api/openapi.json), an OpenAPI 3.1 document generated from this crate - the routes, the request body, every field and every status. It is committed so it can be read without building anything, and a test fails when it no longer matches the code. After changing a route or `TelemetrySample`, regenerate it from the repository root:

```bash
cargo run -p ingest -- openapi > docs/api/openapi.json
```

A route has to be listed in `ApiDoc` in `src/routes/mod.rs` as well as in the router; the integration tests fail on a documented path the router does not serve. The reasoning the document has no room for - the routes, how a device authenticates, the size limits and what an upload does - is in [`docs/development/ingest/architecture.md`](../docs/development/ingest/architecture.md).

## Development

```bash
cargo run -p ingest

cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

`ingest` uses runtime-checked `sqlx::query()` rather than the macros, so **building needs no live database**. The integration tests do: they read `TEST_DATABASE_URL` and `TEST_REDIS_URL` and skip their assertions without them, so a green run that quietly tested nothing is possible - CI fails the job if it detects that. [`docs/development/README.md`](../docs/development/README.md#checking-each-piece) has the commands to run them locally.
