# Car Sensors

[![Ingest Build](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml/badge.svg)](https://github.com/matus-barta/car_sensors/actions/workflows/ingest-build.yml) [![License](https://img.shields.io/github/license/matus-barta/car_sensors)](LICENSE) [![Rust](https://img.shields.io/badge/Rust-stable-orange)](https://www.rust-lang.org/) ![Last Commit](https://img.shields.io/github/last-commit/matus-barta/car_sensors)

Open-source GPS tracking platform.

The project consists of:

- Android app for collecting location data
- Rust ingestion service
- PostgreSQL database
- Valkey cache/storage
- Future web frontend for map visualization

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
db/         Database setup and migrations
ingest/     Telemetry ingestion service
shared/     Shared Rust crate
tools/      Development utilities
www/        Future web frontend
```

### Dev Requirements

- Docker
- Docker Compose
- Rust
- Android Studio

Start infrastructure:

```bash
cd ./tools
docker compose up
```

## License

TBD
