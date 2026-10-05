---
title: "Move the docs site to Mermaid 12"
status: blocked
area: docs
depends_on: []
---

The documentation site renders diagrams with Mermaid 11, not 12, on purpose,
and Renovate is told not to offer 12 (`allowedVersions` in `renovate.json`).

Two reasons, either of which would be enough. [`astro-mermaid`](https://www.npmjs.com/package/astro-mermaid), which renders the
diagrams on the site, declares support for Mermaid 10 and 11 only. And GitHub
renders the same Markdown files with its own Mermaid, which was 11.16.1 ([checked here](https://github.com/orgs/community/discussions/70672)) in
October 2026 - Mermaid 12 changes the default layout to ELK and the default
look, so on 12 every flowchart, state, class and ER diagram would look different
on the site than on GitHub, and a diagram previewed on GitHub could no longer be
trusted to match.

What [12 brings](https://github.com/mermaid-js/mermaid/releases) is mostly defaults: ELK bundled and on by default, the `neo`
look and `redux-color` theme, and UML use case diagrams as a new type. ELK is
already available on 11 through `@mermaid-js/layout-elk`, per diagram, if a
diagram ever needs it.

**Unblocked when both hold:** `astro-mermaid` (or whatever replaces it) supports
Mermaid 12, and GitHub has moved to 12 - its version shows in a ` ```mermaid `
block containing just `info`. Then remove the `allowedVersions` rule and let
Renovate offer the update.
