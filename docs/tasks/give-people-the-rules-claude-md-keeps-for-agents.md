---
title: "Give people the rules CLAUDE.md keeps for agents"
status: backlog
area: docs
depends_on: []
---

The first trim of `.claude/CLAUDE.md` moved its reasoning into `docs/` - the
architecture, the `www` design, development setup, deployment - and left the
file holding rules that link to it. Several of those rules have no human-facing
home at all, though they apply to anyone working here, not only to an agent:

- **Commit conventions** - the topic tag (`www : ...`) and `wip`.
- **How documentation is written** - a `title:` in the frontmatter and no
  `# Heading` under it, pages linking to each other as files, relative links
  rather than bare paths, a primary source for any claim about the outside
  world, a new page going into the sidebar and the index, `/verify-docs` after
  a change.
- **Running a single test** - `cargo test -p ingest <name>`,
  `pnpm test:unit --run <file>`, `pnpm test:e2e -g "<name>"`.
- **How `www` is tested** - the two vitest projects, `expect.requireAssertions`,
  and the end-to-end fixture refusing any database whose name does not end in
  `_test`.
- **Managing the vendored agent skills** with the `skills` CLI.

So the first step is to decide where each of these lives for people - a
development page, a documentation-writing page, possibly a `CONTRIBUTING.md`,
which GitHub links whenever someone opens a pull request or an issue, and
[accepts in `docs/`](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/setting-guidelines-for-repository-contributors)
as well as the root, so it could be a page of the site too - and move it there,
so `CLAUDE.md` links to it as it already does for the rest.

One such home now exists: [Conventions](../development/conventions.md), under
Development, holds the rules that apply to every piece rather than one. It
started with the colour rule, moved there from the `www` page, and is the
obvious place for the commit conventions and the rules for writing
documentation. A rule that concerns one piece belongs on that piece's page
instead, as the `www` testing rules would on
[`docs/development/www/architecture.md`](../development/www/architecture.md).

The second is to look at `CLAUDE.md` itself against what is now known about
these files:

- **Anthropic's [memory documentation](https://code.claude.com/docs/en/memory)**
  targets under 200 lines, prefers headers and bullets to dense paragraphs, and
  notes that `@path` imports organise a long file without reducing what it costs,
  since imported files load at launch too. Instructions that matter for only
  part of the codebase can move into `.claude/rules/` files with `paths:`
  frontmatter, which load only when a matching file is read or edited - the
  `www` rules, the colour rule, the migration rule are candidates. The file is
  143 lines today, but most of them are long paragraphs.
- **Anthropic's [best practices](https://code.claude.com/docs/en/best-practices)**
  ask of every line whether removing it would cause a mistake, and list what to
  leave out: what can be read from the code, long explanations, file-by-file
  descriptions, anything that changes often.
- **[Evaluating AGENTS.md](https://arxiv.org/abs/2602.11988)** (Gloaguen et al.,
  ETH Zurich, final version September 2026) found that context files do not
  generally improve agents' task success and raise inference cost by over 20%;
  their instructions are followed well, but repository overviews do not help.
  That argues for keeping rules and dropping the overview table at the top.
- **[AGENTS.md](https://agents.md/)** is the tool-neutral version of the same
  file, read by Codex, Jules, Aider, Zed and others, and meant to sit beside a
  README written for people. Claude Code reads it only when there is no
  `CLAUDE.md`, or through an `@AGENTS.md` import from one
  ([memory documentation, AGENTS.md](https://code.claude.com/docs/en/memory#agents-md)).
  Moving the rules into `AGENTS.md`, with `CLAUDE.md` importing it, would serve
  any tool [`docs/development/ai-policy.md`](../development/ai-policy.md) allows rather than one.

Whatever moves, the result is checked the way the rest of the documentation is:
lychee and rumdl for the files, `/verify-docs` for the claims. And whether the
smaller file actually works better is worth watching for a while rather than
assuming, which is the paper's own advice.
