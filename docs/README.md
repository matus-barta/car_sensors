---
title: car_sensors documentation
description: How the open-source GPS tracking platform fits together, and how to work on it.
---

An open-source GPS tracking platform: an Android app that records location and
sensor data, a Rust service that receives it, a SvelteKit application that shows
it, and the PostgreSQL database they share.

These pages are plain Markdown in the repository's `docs/` directory, readable
on GitHub as they are. The same files are built into the documentation site by
the Starlight project in [`starlight/`](starlight/), which links them together
and adds search.

## Start here

- [How the pieces fit together](architecture.md) - the four pieces, the one database they share, and why
- [The Android logger](android-app.md) - what the Android logger does, and the platform limitations worth knowing
- [Why the web application image is the size it is](web-image.md) - and what was tried

## Working on it

- [Database migrations and schema synchronization](database-migrations.md) - how the schema is owned and propagated
- [Continuous integration](ci.md) - how the workflows are laid out, and the rules that keep them that way
- [Artificial intelligence usage policy](ai-policy.md) - how AI-assisted changes are made here

## Reference

- [Database schema](schema/README.md) - every table, column and relation, generated from the migrations
