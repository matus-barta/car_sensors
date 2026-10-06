---
title: "Review the architecture documentation with the installed skills"
status: backlog
area: docs
depends_on: []
---

Several of the agent skills vendored in `.agents/skills/` are about
documentation, and none has been run over this project's documentation yet.
The architecture pages are the place to start:
[system architecture](../overview/architecture.md) and the
[`ingest`](../development/ingest/architecture.md) and
[`www`](../development/www/architecture.md) architecture pages. Each skill
brings defaults of its own that disagree with how these docs are laid out, so
the work is as much settling those as running the skills.

## C4 diagrams - `c4-architecture`

The skill draws the [C4 model](https://c4model.com/) in Mermaid: a system
context and a container diagram always, component diagrams only where they add
something, a deployment diagram for a production system. Here that would be the
context and containers on the system architecture page - the one diagram there
now is a plain flowchart - perhaps components for `ingest` and `www`, and a
deployment diagram for [the Compose deployment](../deployment/README.md).

Three things to settle before adopting it:

- **Where the diagrams go.** The skill writes one diagram per file into
  `docs/architecture/c4-*.md`. Pages here live in the directory of their
  sidebar group, so the diagrams belong on the pages above, or beside them,
  instead.
- **Whether they render well enough.** Mermaid
  [calls its C4 diagrams experimental](https://mermaid.js.org/syntax/c4.html),
  with syntax that may change, and gives them a fixed style that does not
  change with the theme - while the site switches its diagrams between light
  and dark, and GitHub draws the same files with its own Mermaid. A C4 diagram
  has to read in both themes, on the site and on GitHub, or the flowcharts
  stay. [Move the docs site to Mermaid 12](move-the-docs-site-to-mermaid-12.md)
  explains why the two Mermaid versions are kept in step.
- **No colour literals.** The skill's examples recolour relationships with
  `UpdateRelStyle` and named colours. The diagrams follow the theme for the
  same reason [the applications do](../development/conventions.md#colours-come-from-the-theme-never-from-a-literal).

## Decision records - `documentation-and-adrs`

Some pages here are already decisions with their reasons:
[the `www` Docker image's size](../deployment/www-docker-image.md),
[database schema ownership](../development/database/ownership.md), and the one
database the system architecture page argues for. None is in an architecture
decision record (ADR) format. Decide whether to adopt one, and if so where: the
skill defaults to `docs/decisions/`, while `improve-codebase-architecture`
looks for ADRs in `docs/adr/` and a domain glossary in `CONTEXT.md`. One
location, chosen deliberately, so the two skills do not start a second.

## The two skills that cannot run yet

`grill-with-docs` and `improve-codebase-architecture` can only be started by
hand, and both hand over to skills that are not vendored: `grill-with-docs` to
`grilling` and `domain-modeling`, `improve-codebase-architecture` to
`codebase-design`. Either add the missing skills with the `skills` CLI or
remove the two, rather than leaving them half-installed.

Whatever pages come out of this are checked with `/verify-docs` and the
[one-kind rule](../development/conventions.md#documentation-one-kind-of-page-at-a-time),
like any other.

**Related.** [Check the remaining pages are one kind each](check-the-remaining-pages-are-one-kind-each.md)
reads the same pages for a different question.
