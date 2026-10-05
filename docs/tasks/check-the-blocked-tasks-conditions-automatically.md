---
title: "Check the blocked tasks' conditions automatically"
status: backlog
area: ci
depends_on: []
---

Three tasks are blocked on someone else's release, and each says what would
unblock it - but only somebody running the check notices, and nobody will
remember to:

| Task | Waiting for | Check |
| --- | --- | --- |
| [Move to TypeScript 7](move-to-typescript-7.md) | `svelte-check`, `typescript-eslint` and `@astrojs/check` to admit TypeScript 7 | `npm view <package> peerDependencies.typescript` |
| [Move the docs site to Mermaid 12](move-the-docs-site-to-mermaid-12.md) | `astro-mermaid` to admit Mermaid 12, and GitHub to render with it | `npm view astro-mermaid peerDependencies.mermaid`; GitHub's version by hand |
| [Move to detekt 2.0 before Gradle 10](move-to-detekt-2-0-before-gradle-10.md) | a stable detekt 2.0 | the `<release>` in the Gradle plugin portal's [`maven-metadata.xml`](https://plugins.gradle.org/m2/dev/detekt/dev.detekt.gradle.plugin/maven-metadata.xml) for `dev.detekt` |

Renovate does not help here: the `allowedVersions` rules that hold TypeScript
and Mermaid back also hide the newer majors from its dependency dashboard, and
it knows nothing about peer dependency ranges.

The shape that fits what exists: a scheduled workflow, monthly, that runs those
checks and opens an issue - or updates one already open - when a condition
turns true, naming the task it unblocks. Everything it needs is public, so it
needs no secrets. Mermaid's GitHub half cannot be checked this way, since the
version GitHub renders with only shows in a rendered page; that one stays a
manual look.

Worth deciding when it is written: whether the conditions live in the workflow,
or in each task's frontmatter where the check and the task cannot drift apart.
