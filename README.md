# Car Sensors

[![License](https://img.shields.io/github/license/matus-barta/car_sensors)](LICENSE) [![Rust](https://img.shields.io/badge/Rust-stable-000000?logo=rust&logoColor=white)](https://www.rust-lang.org/) [![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/) [![Svelte](https://img.shields.io/badge/SvelteKit-web-FF3E00?logo=svelte&logoColor=white)](https://svelte.dev/) [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/) [![Renovate](https://img.shields.io/badge/renovate-enabled-1A1F6C?logo=renovate&logoColor=white)](https://mend.io/renovate/) ![Last Commit](https://img.shields.io/github/last-commit/matus-barta/car_sensors)

[![Ingest - build](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml) [![WWW - validation](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/www-validation.yml) [![Android - validation](https://github.com/matus-barta/car_sensors/actions/workflows/android-validation.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/android-validation.yml) [![Android - migration tests](https://github.com/matus-barta/car_sensors/actions/workflows/android-migration.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/android-migration.yml)

An open-source GPS tracking platform. An Android app records where a vehicle goes and what its sensors read, a Rust service receives it, and a web application shows it on a map - all three sharing one PostgreSQL database.

## Repository layout

| Directory | What it is |
| --- | --- |
| [`android/`](android/) | The Android logger - see [`docs/overview/android-app.md`](docs/overview/android-app.md) |
| [`ingest/`](ingest/README.md) | The Rust service devices upload telemetry to |
| [`shared/`](shared/) | A Rust library crate `ingest` builds on: Postgres and Valkey connections, the embedded migrations, time helpers |
| [`www/`](www/README.md) | The SvelteKit web application: administration and the map |
| [`db/migrations/`](db/migrations/) | The database schema, owned by SQLx - see [`docs/development/database-migrations.md`](docs/development/database-migrations.md) |
| [`docs/`](docs/README.md) | The documentation, the site built from it, and open work in [`docs/tasks/`](docs/tasks/README.md) |
| [`tools/`](tools/) | Local infrastructure and maintenance scripts |

How the pieces fit together, and why they share one database: [`docs/overview/architecture.md`](docs/overview/architecture.md).

## Deployment

It needs Docker with Compose. Both services run from published images, so nothing is built:

```bash
git clone --depth 1 https://github.com/matus-barta/car_sensors.git
cd car_sensors

export ORIGIN="https://cars.example.org"     # where the web application is reached
export BETTER_AUTH_SECRET="$(openssl rand -base64 32)"

docker compose up -d                          # docker compose down stops it
```

The stack refuses to start without those two. It serves plain HTTP and expects a reverse proxy in front - [`docs/deployment/README.md`](docs/deployment/README.md) covers the proxy, the ports, the optional settings and how the schema is migrated.

## Development

### Dev Requirements

- [Docker](https://docs.docker.com/get-started/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Rust toolchain](https://www.rust-lang.org/tools/install)
- [SQLx CLI](https://github.com/launchbadge/sqlx/tree/main/sqlx-cli), installed with the features this project needs as shown in [`docs/development/README.md`](docs/development/README.md#setting-up)
- [Node.js LTS](https://nodejs.org/en/download)
- [pnpm](https://pnpm.io/installation)
- [tbls](https://github.com/k1LoW/tbls), to regenerate the schema documentation in `docs/schema/` after a migration (`brew install tbls`, or see its README for other platforms)
- [actionlint](https://github.com/rhysd/actionlint), to check the workflows in `.github/` after editing them (`brew install actionlint`, or see its README for other platforms)
- [lychee](https://lychee.cli.rs), to check the links between Markdown files (`brew install lychee`, or see its README for other platforms)
- [Android Studio](https://developer.android.com/studio) and JDK 21, when developing the Android application

The schema documentation is compared against CI's output byte for byte, and a
different tbls version may lay the same schema out differently, so use the
version pinned in `.github/workflows/docs-validation.yml`.

Then start the local infrastructure - Postgres, Valkey and pgAdmin - with `cd tools && docker compose up -d`. [`docs/development/README.md`](docs/development/README.md) covers the rest: installing the SQLx CLI, the environment file, and checking each piece the way CI does.

## Documentation

Everything is indexed in [`docs/README.md`](docs/README.md), which is also the home page of the documentation site, published from `main` to [matus-barta.github.io/car_sensors](https://matus-barta.github.io/car_sensors/).

## AI-assisted development

AI tools may help here - with research, code and preparing commits - but a developer reviews every changed line before it is pushed and answers for it, and an `Assisted-by:` trailer records the help. The full policy is [`docs/development/ai-policy.md`](docs/development/ai-policy.md).

## License

Copyright © Matus Barta. Licensed under the GNU Affero General Public License version 3 only (`AGPL-3.0-only`); see [`LICENSE`](LICENSE). If you make a modified version available to users over a network, you must offer them its source code.
