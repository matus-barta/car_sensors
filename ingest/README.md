# ingest

The service devices upload telemetry to. Rust, axum, writing to PostgreSQL and announcing live positions through Valkey.

It has no user interface and no relationship with `www` beyond the database they share. See [`../docs/architecture.md`](../docs/architecture.md) for how that fits together.

## Environment

| Variable | Required | Default | |
| -------- | -------- | ------- | - |
| `DATABASE_URL` | yes | | PostgreSQL connection string |
| `REDIS_URL` | yes | | Valkey connection string |
| `SERVER_IP_PORT` | no | `0.0.0.0:3000` | What to bind to |

The first two are fatal if missing rather than defaulted, because a silent fallback to a local database is a worse failure than not starting.

## API

Everything is served under `/api`. The prefix is not stripped and not optional: a device configured without it gets 404 on every upload, which is a failure that looks exactly like success until somebody notices the backlog growing.

| Route | Auth | |
| ----- | ---- | - |
| `GET /api/health` | none | Answers 200 while the service is up |
| `POST /api/telemetry/upload` | device | A JSON array of samples, gzipped |

### Authentication

A device identifies itself with an `X-Device-ID` header, checked against an active row in `known_devices`. There is nothing else: the identifier is also the credential. That is a deliberate decision for a private deployment rather than an oversight, and `todo.md` carries the case for replacing it with a per-device token.

The answers are meant to be told apart by a client:

- **401** - no header, or a device this server does not know
- **403** - a device it knows and has deactivated
- **400** - a body it could not parse
- **413** - a body larger than the limits below
- **500** - the database refused the batch

The distinction matters at the other end: "send smaller batches" and "this will never be accepted" call for different behaviour, and 401/403/404 say nothing about the rows at all.

### Request size

Uploads are limited to **4 MiB on the wire** and **32 MiB once decompressed**. Both are needed: telemetry compresses extremely well - around 3 KB for 500 samples - so a small compressed body can expand far enough to matter, and the JSON extractor buffers the whole thing before parsing it.

A reverse proxy in front of this needs a body limit at least as large, or it will reject a device's backlog before the service ever sees it.

## What an upload does

The batch is written to `telemetry_samples` in one transaction, in chunks that stay under PostgreSQL's bind-parameter ceiling. Insertion is `ON CONFLICT DO NOTHING` against `(device_id, id)`, which makes a re-uploaded batch harmless - a device that never received a response is free to send it again.

`known_devices.last_seen_at` is touched at most once per device per **30 seconds**, throttled through Valkey. A device uploading every few seconds does not need to write that column every time.

If the batch stored anything, the newest sample carrying a position is published to Valkey as the device's live location, under a **15 minute** expiry. That step is best effort: the durable copy is already committed, and a failure to announce leaves the upload successful. It is guarded against a device clock running ahead, and against an older batch arriving after a newer one and dragging a map marker backwards.

## Development

```bash
cargo run -p ingest

cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

`ingest` uses runtime-checked `sqlx::query()` rather than the macros, so **building needs no live database**. The integration tests do: they read `TEST_DATABASE_URL` and `TEST_REDIS_URL` and skip their assertions without them, so a green run that quietly tested nothing is possible - CI fails the job if it detects that. `cd tools && docker compose up -d` provides both locally.
