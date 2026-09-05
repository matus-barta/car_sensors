# Car Sensors

[![License](https://img.shields.io/github/license/matus-barta/car_sensors)](LICENSE) [![Rust](https://img.shields.io/badge/Rust-stable-000000?logo=rust&logoColor=white)](https://www.rust-lang.org/) [![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/) [![Svelte](https://img.shields.io/badge/SvelteKit-web-FF3E00?logo=svelte&logoColor=white)](https://svelte.dev/) [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/) [![Renovate](https://img.shields.io/badge/renovate-enabled-1A1F6C?logo=renovate&logoColor=white)](https://mend.io/renovate/) ![Last Commit](https://img.shields.io/github/last-commit/matus-barta/car_sensors)

[![Ingest - build](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml) [![WWW - validation](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml) [![Android - validation](https://github.com/matus-barta/car_sensors/actions/workflows/android-validation.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/android-validation.yml) [![Android - migration tests](https://github.com/matus-barta/car_sensors/actions/workflows/android-migration.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/android-migration.yml)

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

### Running behind a reverse proxy

Exposing the services publicly is left to the operator, and so is TLS. The
Compose file publishes plain HTTP on the loopback interface; putting a reverse
proxy in front of it is the expected way to serve it to the internet.

Two things the proxy has to get right:

- **`ingest` serves everything under `/api`.** Health is `/api/health` and
  uploads are `/api/telemetry/upload`. Route that prefix to the ingestion
  service and leave the rest to the web application - a single hostname works if
  the proxy selects by path prefix. Do not strip the prefix: the service expects
  to receive it. The device's configured base URL must include whatever prefix
  the deployment uses.
- **Request bodies must be allowed through.** `ingest` accepts uploads up to
  4 MiB on the wire, expanding to 32 MiB once decompressed. A proxy with a
  smaller body limit will reject a device's backlog before the service sees it.

### Scope of the Compose deployment

The Compose file currently starts PostgreSQL, Valkey, pgAdmin and the Rust
ingestion service. The SvelteKit web application is **not** part of it yet and
has no container image, so `www/` must be built and run separately:

```bash
cd www
pnpm install
pnpm build
node build
```

The web application needs `DATABASE_URL`, `ORIGIN` and `BETTER_AUTH_SECRET` in
its environment. See `www/README.md`.

## Development

### Repository Structure

```text
android/    Android application
db/         Authoritative PostgreSQL migrations
docs/       Topic documentation
ingest/     Rust telemetry ingestion service (see ingest/README.md)
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
- Android Studio and JDK 21, when developing the Android application

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

## Validation

Each piece is checked the same way locally as it is in CI, so a green run here
means a green run there.

Rust, from the repository root:

```bash
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

The `ingest` integration tests need a reachable database and cache, and skip
their assertions without one. `cd tools && docker compose up -d` provides both.

Web application:

```bash
cd www
pnpm check    # svelte-kit sync and svelte-check
pnpm lint     # prettier and eslint
pnpm test     # unit, component and end-to-end
```

Android application:

```bash
cd android
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest
```

That is formatting, static analysis, Android lint and the unit tests, which run
on the JVM and need no device. The instrumented tests do need one:

```bash
./gradlew connectedDebugAndroidTest        # a handset over adb
./gradlew api30atdDebugAndroidTest         # or a Gradle-managed emulator
```

Android Studio has the first of these as a shared run configuration, so neither
needs typing. See [`docs/android-app.md`](docs/android-app.md).

## Documentation

- [`docs/architecture.md`](docs/architecture.md) - how the four pieces fit together and why they share one database
- [`docs/android-app.md`](docs/android-app.md) - what the Android logger does, and the platform limitations worth knowing
- [`docs/database-migrations.md`](docs/database-migrations.md) - how the schema is owned and propagated
- [`docs/ai-policy.md`](docs/ai-policy.md) - how AI-assisted changes are made here

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
