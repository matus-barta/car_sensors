---
title: "Take pgAdmin out of the deployment Compose file"
status: next
area: tools
depends_on: []
---

The root `docker-compose.yml` is what a server runs, and it starts pgAdmin
alongside the database: with the default credentials `pgadmin@example.com` /
`pgadmin` set in the file, and published as `8888:8888` - on every network
interface, not only loopback. Postgres and Valkey are bound to `127.0.0.1`;
pgAdmin is not. On a host without a firewall in front of it, anyone who can
reach port 8888 gets a database administration console with a known password,
already connected through `tools/pgadmin_config/servers.json`.

pgAdmin is a development tool and belongs in `tools/docker-compose.yml`, which
already has its own. What to remove from the root file:

- the `pgadmin` service, with its environment, port and volume mounts;
- the `pgadmin_data` volume it declares.

Then the places that describe the deployment as including it:

- the root `README.md`, where "Scope of the Compose deployment" lists it among
  what the Compose file starts;
- `.claude/CLAUDE.md`, whose description of the deployment file lists it;
- [`docs/web-image.md`](../web-image.md), which counts it among what the
  server already runs.

An operator who wants to inspect the database in production can still reach it
from the host or over an SSH tunnel to `127.0.0.1:5432`, without a web console
exposed to the network.

Worth checking while doing it: `ingest` and `www` are published on every
interface too (`3000:3000` and `3001:3000`). The README says the stack publishes
"plain HTTP on the loopback interface" and expects a reverse proxy in front;
binding both to `127.0.0.1` would make that true and keep the services from
being reached around the proxy.
