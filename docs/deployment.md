---
title: "Deploying with Docker Compose"
---

The root `docker-compose.yml` runs the whole platform from published images; nothing has to be built. It needs Docker with Compose, and two variables that have no default - the stack refuses to start without them:

```bash
git clone --depth 1 https://github.com/matus-barta/car_sensors.git
cd car_sensors

export ORIGIN="https://cars.example.org"     # where the web application is reached
export BETTER_AUTH_SECRET="$(openssl rand -base64 32)"

docker compose up -d                          # docker compose down stops it
```

## Running behind a reverse proxy

Exposing the services publicly is left to the operator, and so is TLS. The
Compose file publishes plain HTTP - `ingest` on port 3000, the web application
on 3001 - and putting a reverse proxy in front of it is the expected way to
serve it to the internet. Those two ports, and pgAdmin's 8888, are bound to
every network interface today; only Postgres and Valkey are limited to
loopback. Until that changes - see
[`docs/tasks/take-pgadmin-out-of-the-deployment-compose-file.md`](tasks/take-pgadmin-out-of-the-deployment-compose-file.md) -
keep them closed to the outside with a firewall, so the services cannot be
reached around the proxy.

Two things the proxy has to get right:

- **Route `/api` to `ingest`, and leave the rest to the web application.** A
  single hostname works if the proxy selects by path prefix. Do not strip the
  prefix: [`ingest` serves everything under it](../ingest/README.md#api). The
  device's configured base URL must include whatever prefix the deployment
  uses.
- **Allow a request body at least as large as `ingest` accepts** - its
  [request size limits](../ingest/README.md#request-size). A proxy with a
  smaller body limit rejects a device's backlog before the service sees it.

## What the Compose file runs

The Compose file starts PostgreSQL, Valkey, pgAdmin, the Rust ingestion service
and the SvelteKit web application. Both services are published as images and
neither has to be built to deploy.

`ORIGIN` has to match the address in the browser, because Better Auth checks it
and a mismatch rejects sign-in. `BETTER_AUTH_SECRET` deliberately has no
fallback: a predictable signing key is worse than a service that will not start.

Everything else is optional. `PUBLIC_OSM_VECTOR_TILE_URL` and
`PUBLIC_OSM_STYLE_URL` point the map at your own tile server. They fall back to
the public OpenStreetMap tiles and to the VersaTiles "colorful" style, because
OpenStreetMap serves its own style only to its own sites and localhost. They
are read when the container starts rather than baked into the image, so one
image serves every deployment.

`TRUSTED_PROXIES` matters once the application sits behind more than one proxy.
Sign-in is rate limited per client address, which Better Auth reads from
`X-Forwarded-For`; it trusts that header on its own only when it holds a single
address. A lone reverse proxy that sets the header itself needs nothing here.
Behind a CDN such as Cloudflare, list the CDN's published ranges
(comma-separated IPs or CIDR ranges) *and* have the reverse proxy trust the
same ranges, so it passes the header on rather than replacing it with the CDN's
address. Missing either half leaves every visitor sharing one limit.

Migrations need no separate step. They are embedded into the `ingest` binary by
`sqlx::migrate!` and applied when it connects, so bringing the stack up brings
the schema up with it. That is also why `www` waits for `ingest` to be healthy
rather than only for the database - it is not a runtime dependency between the
two, only an ordering one, and without it the web application could query a
table before the migration that changed it had run.
