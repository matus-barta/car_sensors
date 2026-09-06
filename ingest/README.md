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

A device presents two things, and they do different jobs:

```http
Device-Id: 00000000-0000-4000-8000-000000000000
Authorization: Bearer <token>
```

`Device-Id` names the row in `known_devices` and is not a secret. The token is, and it is what proves the request came from the phone that row belongs to. Only its SHA-256 is stored, as lowercase hex, so a copy of the database yields nothing that can be replayed. The token is 32 random bytes rendered as unpadded base64url, and the hash covers that string exactly as it arrives, so both ends must agree on the encoding.

Keeping them apart is what makes a credential withdrawable. Rotating a token leaves `device_id` alone, so replacing a handset or locking out a lost one costs the vehicle none of its history.

A row whose `token_hash` is `NULL` cannot authenticate. That is not a transitional state to be tolerated: accepting an identity on its own is the thing this scheme exists to prevent, so a device registered before tokens existed stops uploading until one is minted for it.

Refusals follow [RFC 6750](https://datatracker.ietf.org/doc/html/rfc6750#section-3) and carry a `WWW-Authenticate: Bearer` challenge, so a client can tell them apart without a private convention:

| Status | `error` | Means |
| --- | --- | --- |
| **401** | *(none)* | Nothing was presented - no identity, or no bearer token |
| **401** | `invalid_token` | A token was presented and refused, or the identity is unknown |
| **403** | `insufficient_scope` | The token is good and the device is deactivated |
| **400** | | A body it could not parse |
| **413** | | A body larger than the limits below |
| **500** | | The database refused the batch |

An unknown identity is answered exactly as a bad token is, on purpose: distinguishing them would turn the endpoint into a way of discovering which devices exist.

The distinction matters at the other end. "Re-pair this phone" and "this vehicle is retired" call for different behaviour and so do "send smaller batches" and "this will never be accepted", while none of the authentication answers say anything about the rows themselves.

### Request size

Uploads are limited to **4 MiB on the wire** and **32 MiB once decompressed**. Both are needed: telemetry compresses extremely well - around 3 KB for 500 samples - so a small compressed body can expand far enough to matter, and the JSON extractor buffers the whole thing before parsing it.

A reverse proxy in front of this needs a body limit at least as large, or it will reject a device's backlog before the service ever sees it.

## What an upload does

The batch is written to `telemetry_samples` in one transaction, in chunks that stay under PostgreSQL's bind-parameter ceiling. Insertion is `ON CONFLICT DO NOTHING` against `(device_id, id)`, which makes a re-uploaded batch harmless - a device that never received a response is free to send it again.

`known_devices.last_seen_at` is written at most once per device per **30 seconds**, tracked through Valkey. A device uploading every few seconds does not need to write that column every time.

This limits a column write and nothing else. No request is rejected, delayed or shed by it, and `ingest` has no rate limiting of any kind - see `todo.md`.

If the batch stored anything, the newest sample carrying a position is published to Valkey as the device's live location, under a **15 minute** expiry. That step is best effort: the durable copy is already committed, and a failure to announce leaves the upload successful. It is guarded against a device clock running ahead, and against an older batch arriving after a newer one and dragging a map marker backwards.

## Development

```bash
cargo run -p ingest

cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

`ingest` uses runtime-checked `sqlx::query()` rather than the macros, so **building needs no live database**. The integration tests do: they read `TEST_DATABASE_URL` and `TEST_REDIS_URL` and skip their assertions without them, so a green run that quietly tested nothing is possible - CI fails the job if it detects that. `cd tools && docker compose up -d` provides both locally.
