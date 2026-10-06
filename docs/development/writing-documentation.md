---
title: "Writing documentation"
---

The rules for the documentation in `docs/`, and for the Markdown elsewhere in the repository - READMEs, tasks, `CLAUDE.md` - where they apply. Each comes with its reason; the commands that check them are under [checking each piece](README.md#checking-each-piece).

## Where a page lives

Documentation is the Markdown in `docs/`, written to be read on GitHub as it is. The Starlight project in [`docs/starlight/`](../starlight/) builds the same files into a site without moving them - [`content.config.ts`](../starlight/src/content.config.ts) loads them from `docs/` itself - so a page has two readers and has to work for both.

Each group of the site's sidebar is a directory, and a directory's `README.md` is the group's first page: `docs/development/www/` holds the `www` pages under Development. A new page goes into the directory of its group, into the sidebar in [`astro.config.ts`](../starlight/astro.config.ts), and into the list in [`docs/README.md`](../README.md), which is the same index for GitHub.

## How a page is written

**Every page starts with `title:` frontmatter and no `# Heading`.** Starlight renders the title itself, and a heading would show it twice.

**Diagrams are Mermaid, not ASCII art** - a ` ```mermaid ` block, which both the site and GitHub draw, and which follows their light and dark themes. Which Mermaid version that is, and why the two are kept in step, is in [move the docs site to Mermaid 12](../tasks/move-the-docs-site-to-mermaid-12.md).

### One kind of page at a time

Each page is one of the kinds [Diátaxis](https://diataxis.fr/) describes, chosen by what its reader came for:

| Kind | The reader wants to | Here |
| --- | --- | --- |
| How-to guide | get a task done | [development setup](README.md), [database migrations](database/migrations.md), [testing `www`](www/testing.md), [working on the Android app](android/README.md), [deployment](../deployment/README.md) |
| Explanation | understand why | the [system](../overview/architecture.md), [`ingest`](ingest/architecture.md) and [`www`](www/architecture.md) architecture, [the Android logger](../overview/android-app.md), [database schema ownership](database/ownership.md), [Android build and CI](android/build.md), [continuous integration](ci.md), [writing instructions for AI agents](writing-agent-instructions.md), [the `www` Docker image](../deployment/www-docker-image.md) |
| Reference | look something up | the [database schema](../schema/README.md), the [`ingest` API](../api/openapi.json), the [conventions](conventions.md), this page, the [AI usage policy](ai-policy.md) |
| Tutorial | learn by doing | none yet |

Steps and the reasons for them go on separate pages that link to each other. A how-to guide keeps to what to do and what to check, and says where the reasons are; an explanation says why, and links to the steps. A sentence of reasoning in a how-to is fine, and so is the short list of rules an explanation arrives at. A whole section of the other kind is not - that is the sign a page wants splitting, as [database schema ownership](database/ownership.md) was split from [database migrations](database/migrations.md).

The sections themselves - Overview, Deployment, Development and the pieces under it - are by area, not by kind. That is this project's choice rather than Diátaxis's, which [prescribes a structure by kind](https://diataxis.fr/how-to-use-diataxis/), grown from the inside as pages are improved rather than laid out in advance. Pages of one kind gathering in an area would be the time to reconsider.

## Links

**Pages link to each other as files** - `[CI](../development/ci.md#runners-are-pinned)` - so the link works on GitHub, and the site rewrites it into the page's address. Its build fails on a link between pages that does not resolve.

**Anything else in the repository is linked with a relative Markdown link too**, not named as a bare path in backticks. [lychee](https://lychee.cli.rs) checks every such link, and the heading it points at, in CI - settings in [`lychee.toml`](../../lychee.toml). A bare path is only checked by whoever reads it.

**A claim about anything outside the repository links its primary source** - another project's behaviour, a date, a policy, a version upstream - so it can be checked again later rather than searched for. lychee runs offline, so those links are not checked automatically: they are only as good as the last time someone followed them.

## Generated pages

[`docs/schema/`](../schema/README.md) is written by tbls from the migrated database, and [`docs/api/openapi.json`](../api/openapi.json) by `ingest` itself. Both are committed, never edited by hand, and compared with a fresh copy in CI. How each is regenerated is in [database migrations](database/migrations.md#synchronizing-the-web-schema) and the [`ingest` README](../../ingest/README.md#api).

## Tasks

[`docs/tasks/`](../tasks/README.md) holds work that is understood but not scheduled yet, one file per task. Its README defines the fields and the rest: a finished task is deleted rather than marked done, a task is referred to by its path, and `depends_on` names only what the task's own text says it needs. The site's build rejects a task that breaks them.

## After writing

Run `/verify-docs` on what changed. It checks each factual claim against the code and cites quotes that a script then confirms exist in the files named, so a verdict cannot rest on memory - a sentence can read perfectly and still be wrong about the code. It is this project's own skill, in [`.claude/skills/verify-docs/`](../../.claude/skills/verify-docs/SKILL.md), and edited by hand rather than vendored.

Then the checks under [checking each piece](README.md#checking-each-piece): rumdl for the Markdown's style, lychee for the links, and the site's own check and build.
