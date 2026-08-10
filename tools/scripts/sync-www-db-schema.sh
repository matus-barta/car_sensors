#!/usr/bin/env sh

set -eu

echo "Applying SQLx migrations..."
sqlx migrate run --source db/migrations

echo "Regenerating the Drizzle schema..."
cd www
pnpm db:sync

echo "Checking the generated frontend database schema..."
pnpm check

echo "Database schema synchronization completed."