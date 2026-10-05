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
`.github/actions/setup-www` already makes for four job references.

Two constraints to remember when that day comes. Service containers cannot live
in a composite action, because `services` is a job-level key; sharing the
Postgres and Valkey setup is what the reusable workflow is for. And the Rust
setup inside `setup-www` should stay where it is: it exists to install the SQLx
CLI and wants neither the components nor the workspace cache, so merging the two
would produce one action with flags selecting between unrelated behaviours.
