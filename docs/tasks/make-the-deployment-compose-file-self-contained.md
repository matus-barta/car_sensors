---
title: "Make the deployment Compose file self-contained"
status: backlog
area: tools
depends_on: [take-pgadmin-out-of-the-deployment-compose-file]
---

Deploying means cloning the repository, because the root `docker-compose.yml`
bind-mounts files from `tools/`: pgAdmin's `servers.json` and
`preferences.json`, and `tools/valkey_config/valkey.conf`, which Valkey is
started with (`command: valkey-server /etc/valkey/valkey.conf`). Postgres,
`ingest` and `www` need nothing but their images. Download the Compose file on
its own and Docker creates empty directories where those files should be.

Taking pgAdmin out of the deployment removes the first two. That leaves
`valkey.conf`, and it holds no decision of this project's own: it is the stock
Valkey 9.0 [`valkey.conf`](https://github.com/valkey-io/valkey/blob/9.0/valkey.conf)
with the comments stripped, the `bind 127.0.0.1 -::1` line removed and
`protected-mode` set to `no` - the two changes that let other containers
connect. The official image already makes both of them without a file: its
[Dockerfile](https://github.com/valkey-io/valkey-container/blob/mainline/9.0/alpine/Dockerfile)
compiles Valkey with protected mode off "as it is unnecessary in context of
Docker", and with no `bind` directive Valkey listens on every interface of the
container ([`CONFIG_DEFAULT_BINDADDR`](https://github.com/valkey-io/valkey/blob/9.0/src/server.h#L148)
is `{"*", "-::*"}`). The working directory, which `dir ./` in the file resolves against,
is the image's `WORKDIR /data` either way, where the `valkey_data` volume is
mounted.

So the likely change is to drop the `valkey.conf` mount and the `command` line
from the root Compose file, and let the image run its default `valkey-server`.
Check it by bringing the stack up and watching `ingest` upload and `www`'s live
tracking work. If a setting does turn out to be wanted, pass it as a flag on
`command`, or inline it with a top-level `configs:` entry using `content:`
([Compose 2.23.1 and later](https://docs.docker.com/reference/compose-file/configs/)),
rather than mounting a file again.

Once the Compose file needs nothing beside it, change the deployment
instructions in the root `README.md` and
[`docs/deployment/README.md`](../deployment/README.md) from a shallow `git clone` to
downloading the raw file from `main`. Say how to update at the same time: with
no clone there is no `git pull`, so a new Compose file is fetched the same way,
and `docker compose pull` brings newer images.

`tools/docker-compose.yml` mounts the same `valkey.conf`. Deleting the file
means changing that one too, or leaving the file to the development setup
alone.
