# Car Sensors

[![License](https://img.shields.io/github/license/matus-barta/car_sensors)](LICENSE) [![Rust](https://img.shields.io/badge/Rust-stable-orange)](https://www.rust-lang.org/) ![Last Commit](https://img.shields.io/github/last-commit/matus-barta/car_sensors)

[![Ingest Build](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml) [![WWW - database schema consistency](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml)

Open-source GPS tracking platform.

The project consists of:

- Android application for collecting location and sensor data
- Rust telemetry ingestion service
- PostgreSQL database
- Valkey cache and storage
- SvelteKit web frontend for administration and map visualization

## Deployment

### Deployment Requirements

- Docker
- Docker Compose

### Start

```bash
git clone https://github.com/matus-barta/car_sensors.git
cd car_sensors
docker compose up -d
```

### Stop

```bash
docker compose down
```

## Development

### Repository Structure

```text
android/    Android application
db/         Authoritative PostgreSQL migrations
ingest/     Rust telemetry ingestion service
shared/     Shared Rust crate
tools/      Development and synchronization utilities
www/        SvelteKit web application
```

### Dev Requirements

- Docker
- Docker Compose
- Rust toolchain
- SQLx CLI
- Node.js LTS
- pnpm
- Android Studio, when developing the Android application

### Install SQLx CLI

Install SQLx CLI with PostgreSQL and Rustls support:

```bash
cargo install sqlx-cli \
    --no-default-features \
    --features rustls,postgres
```

### Install web dependencies

```bash
cd www
pnpm install
```

### Start infrastructure

```bash
cd ./tools
docker compose up
```

## AI-assisted development

AI tools may be used to assist with research, documentation, analysis, and code suggestions. They are not autonomous contributors or decision-makers for this project.

All AI-assisted changes must be understood, reviewed, and validated by a human before being committed. AI agents must not create commits, push changes, merge pull requests, deploy releases, apply production migrations, access project secrets, or modify repository settings.

The human contributor remains fully responsible for the correctness, security, licensing, and maintainability of every submitted change.

See docs/ai-policy.md for the complete policy.

## License

Copyright © Matus Barta.

This project is licensed under the **GNU Affero General Public License version 3 only**.

The corresponding SPDX license identifier is:

```text
AGPL-3.0-only
```

You may use, study, modify, and redistribute this software under the terms of the GNU Affero General Public License version 3.

If you modify the software and make the modified version available to users over a network, you must make the corresponding source code available to those users as required by the license.

See the LICENSE file for the complete license terms.
