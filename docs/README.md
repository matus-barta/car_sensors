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
and adds search. Each section below is a directory of its own, so the
directories read the same way the site's sidebar does.

## Overview

- [System architecture](overview/architecture.md) - the four pieces, the one database they share, and why
- [The Android logger](overview/android-app.md) - what the Android logger does, and the platform limitations worth knowing

## Deployment

- [Deploying with Docker Compose](deployment/README.md) - the Compose file, the reverse proxy in front of it, and the settings it takes
- [Why the web application's Docker image is the size it is](deployment/www-docker-image.md) - and what was tried

## Development

- [Development setup](development/README.md) - setting up a machine, the environment file, and checking each piece the way CI does
- [Continuous integration](development/ci.md) - how the workflows are laid out, and the rules that keep them that way
- [Conventions](development/conventions.md) - rules that hold across every piece: where colours come from, and keeping a page of documentation to one kind
- [Artificial intelligence usage policy](development/ai-policy.md) - how AI-assisted changes are made here

### Database

- [Database schema ownership](development/database/ownership.md) - who may change the schema, and why the web application's copy of it only follows
- [Database migrations and schema synchronization](development/database/migrations.md) - creating and applying a migration, and regenerating what is generated from it

### Android

- [Working on the Android app](development/android/README.md) - checking, testing and installing the app
- [Android build and CI](development/android/build.md) - the SDK levels, the linters' settings, how CI runs the devices, and the parts of the build that are not what they look like

### ingest

- [ingest architecture](development/ingest/architecture.md) - its routes, how a device authenticates, the size limits and what an upload does

### www

- [www architecture](development/www/architecture.md) - the decisions behind the `www` code, and why

## Reference

- [Database schema](schema/README.md) - every table, column and relation, generated from the migrations

## Open work

- [Tasks](tasks/README.md) - work that is understood but not scheduled yet, one page per task
