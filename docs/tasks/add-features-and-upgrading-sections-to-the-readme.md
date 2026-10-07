---
title: "Add features and upgrading sections to the README"
status: backlog
area: docs
depends_on: []
---

Two sections other self-hosted trackers' READMEs have and this one does not.

**Features.** [Dawarich](https://github.com/Freika/dawarich#features) lists
what it does, grouped by area. The README's opening paragraph covers this
project today; a list earns its place once there is more to say than a
paragraph holds - several vehicles and accounts, pairing a phone with a QR code,
what the logger does as the battery drains, trips once they exist.

**Upgrading.** [Hauk](https://github.com/bilde2910/Hauk#upgrading-to-newer-versions)
and Dawarich say how to move a running installation to a newer version. Here
that is pulling the newer images and recreating the containers; the schema
follows on its own, since `ingest` applies its migrations when it starts
([deployment](../deployment/README.md#what-the-compose-file-runs)). The steps
belong in the deployment page, with the README pointing to them, and would say
how to roll back - which migrations make harder, and which is worth thinking
through before writing.
