---
title: "Extract a setup-rust action once a second Rust job exists"
status: backlog
area: ci
depends_on: []
---

`ingest-validation.yml` installs the toolchain with clippy and rustfmt and warms
`Swatinem/rust-cache` inline. That is one job, so there is nothing to share and
a composite action would be indirection for its own sake.

The moment a second Rust job appears - splitting formatting, clippy and tests to
run in parallel, adding `cargo audit`, or a coverage job - those two steps become
worth extracting into `.github/actions/setup-rust`, the same trade
`.github/actions/setup-www` already makes for the two `www` validation jobs.

When that day comes, the constraints are the general ones for shared setup in
[`docs/development/ci.md`](../development/ci.md#shared-setup-is-a-composite-action-one-purpose-each):
the Postgres and Valkey services cannot move into the action, and the action
stays separate from `setup-sqlx`, which builds the SQLx CLI and wants neither
the components nor the workspace cache.
