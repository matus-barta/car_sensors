#!/usr/bin/env sh

set -eu

: "${DATABASE_URL:?DATABASE_URL must point at the database to document}"

# tbls connects through lib/pq, which requires TLS unless told otherwise, and
# neither the local nor the CI database offers it. This script only ever runs
# against one of those, so a URL without an explicit sslmode gets one.
case "$DATABASE_URL" in
  *sslmode=*) TBLS_DSN="$DATABASE_URL" ;;
  *\?*) TBLS_DSN="$DATABASE_URL&sslmode=disable" ;;
  *) TBLS_DSN="$DATABASE_URL?sslmode=disable" ;;
esac
export TBLS_DSN

echo "Applying SQLx migrations..."
sqlx migrate run --source db/migrations

echo "Regenerating the schema documentation..."
tbls doc --config .tbls.yml --rm-dist

echo "Schema documentation generated in docs/schema."
