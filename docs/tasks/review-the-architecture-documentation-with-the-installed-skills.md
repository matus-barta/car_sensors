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

## Decision records - `documentation-and-adrs` and `domain-modeling`

Some pages here are already decisions with their reasons:
[the `www` Docker image's size](../deployment/www-docker-image.md),
[database schema ownership](../development/database/ownership.md), and the one
database the system architecture page argues for. None is in an architecture
decision record (ADR) format. Decide whether to adopt one, and if so where.
The skills disagree: `documentation-and-adrs` defaults to `docs/decisions/`,
while `domain-modeling` - and `improve-codebase-architecture`, which reads what
it writes - use `docs/adr/`, numbered `0001-slug.md`. One location, chosen
deliberately, so they do not start two.

Wherever they go, they are pages of this site. `domain-modeling`'s template
opens with a `# Heading` and no frontmatter, which the build rejects: a page
here needs `title:` frontmatter and no heading of its own, belongs to a sidebar
group, and is listed in [`docs/README.md`](../README.md). The same skill keeps
a domain glossary in a `GLOSSARY.md` at the repository root, created the first
time a term is settled; whether that sits at the root or in `docs/` is part of
the same decision.

## Architecture review - `improve-codebase-architecture`

Started by hand, it looks for shallow modules worth deepening and walks
through one with `grilling`, keeping the glossary and ADRs current through
`domain-modeling` and using `codebase-design`'s vocabulary. It is the review
half of this task rather than the documentation half, and is best run once the
ADR location above is settled, since it writes there.

Whatever pages come out of this are checked with `/verify-docs` and the
[one-kind rule](../development/writing-documentation.md#one-kind-of-page-at-a-time),
like any other.

**Related.** [Move the agent instructions to AGENTS.md, with the skills](move-the-agent-instructions-to-agents-md-with-the-skills.md)
restructures the same vendored skills this task runs.
