---
title: "Continuous integration"
---

How the workflows in `.github/` are laid out, and the rules that keep them that way. Check any change to them with `actionlint` before committing.

## One validation workflow per piece

Each piece of the project has a `<Piece> - validation` workflow that runs on pull requests touching it, and exposes `workflow_call`. Where a piece produces an image, `<Piece> - build` runs on pushes to `main` and *calls* the validation workflow rather than repeating it, so an image is only built once its checks have passed.

| Workflow | Runs on | Calls |
| --- | --- | --- |
| [`ingest-validation.yml`](../.github/workflows/ingest-validation.yml) | pull requests | |
| [`ingest-build.yml`](../.github/workflows/ingest-build.yml) | pushes to `main` | `ingest-validation.yml` |
| [`www-validation.yml`](../.github/workflows/www-validation.yml) | pull requests, pushes to `main` | |
| [`www-build.yml`](../.github/workflows/www-build.yml) | pushes to `main` | `www-validation.yml` |
| [`android-validation.yml`](../.github/workflows/android-validation.yml) | pull requests, pushes to `main` | |
| [`android-migration.yml`](../.github/workflows/android-migration.yml) | pull requests, pushes to `main`, by hand | |
| [`android-dependency-graph.yml`](../.github/workflows/android-dependency-graph.yml) | pushes to `main` | |
| [`docs-validation.yml`](../.github/workflows/docs-validation.yml) | pull requests, every push to `main` | |

`docs-validation.yml` is the one that belongs to no piece: it checks what is shared - documentation generated from the database, and the links between Markdown files.

## Generated output is checked where it belongs

Anything generated and committed is regenerated in CI and compared with the committed copy, so it cannot drift from its source. The check lives with whoever owns the output:

| Output | Generated from | Checked by |
| --- | --- | --- |
| `www/src/lib/server/db/generated/` | the migrated database, by Drizzle | `www-validation.yml` |
| `docs/schema/` | the migrated database, by tbls | `docs-validation.yml` |
| `docs/api/openapi.json` | the `ingest` source, by utoipa | `cargo test`, in `ingest-validation.yml` |

A generator whose output is compared byte for byte is pinned to an exact version, because a newer one may lay out the same input differently and fail the check on formatting rather than on a change.

## Runners are pinned

Every job runs on `ubuntu-24.04`, not `ubuntu-latest`. A new image arriving unannounced is a failure that looks like a code change; pinned, it arrives when somebody decides it should. Moving to the next one is [a task of its own](../tasks/move-the-workflows-to-ubuntu-26-04.md).

## Shared setup is a composite action, one purpose each

Steps that several jobs repeat live in `.github/actions/`:

| Action | Sets up |
| --- | --- |
| [`setup-www`](../.github/actions/setup-www/action.yml) | the `www` dependencies through `setup-pnpm`, Playwright, and the SQLx CLI through `setup-sqlx` |
| [`setup-pnpm`](../.github/actions/setup-pnpm/action.yml) | Node.js and pnpm, and one package's dependencies from its lockfile - `www` or the documentation site |
| [`setup-sqlx`](../.github/actions/setup-sqlx/action.yml) | the SQLx CLI, the only tool allowed to apply `db/migrations` |
| [`setup-android`](../.github/actions/setup-android/action.yml) | the JDK, Gradle and the Android SDK licences |
| [`setup-android-device`](../.github/actions/setup-android-device/action.yml) | the emulator, KVM and a Gradle managed device |

Three rules follow from how they have been used:

- **Extract at the second user, not before.** A composite action used by one job is indirection for its own sake. The moment a second job needs the same steps, they move into an action.
- **One purpose per action.** Two setups that look alike but want different things stay apart. `setup-sqlx` installs a toolchain only to build the CLI - no components, no workspace cache. A job that builds the Rust workspace wants `clippy`, `rustfmt` and `Swatinem/rust-cache`, so when a second such job appears it gets a `setup-rust` of its own (see [the task](../tasks/extract-a-setup-rust-action-once-a-second-rust-job-exists.md)) rather than flags added to `setup-sqlx` that select between unrelated behaviours.
- **Services cannot be shared this way.** `services` is a job-level key, so a Postgres or Valkey container cannot live in a composite action. Each job declares its own; sharing that setup is what a reusable workflow is for.
