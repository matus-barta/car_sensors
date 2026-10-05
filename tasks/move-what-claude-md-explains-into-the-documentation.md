---
title: "Move what CLAUDE.md explains into the documentation"
status: backlog
area: docs
depends_on: []
---

`.claude/CLAUDE.md` has grown to 148 lines across seven sections - what this
is, the migration rule, commands, environment, `www` architecture, testing and
conventions - and a good part of it is explanation rather than instruction. Its
opening section restates [`docs/architecture.md`](../docs/architecture.md)
almost word for word: one database on purpose, data moving through it rather
than through calls, one writer per table. Two copies of the same reasoning drift
apart, and only one of them is read by people.

The split to aim for: `CLAUDE.md` keeps what an agent must do or must not do,
each rule in a sentence or two, with a link to the document that explains it.
The explanation moves to `docs/`, where it is part of the documentation site,
link-checked, and read by people as well. Candidates:

- **What this is** - already in `docs/architecture.md`; leave a pointer.
- **`www` architecture** - remote functions, deriving from `clock`, `error()`
  in remote functions, the app shell, auth, setup state, shadcn-svelte. These
  read as a `www` architecture document with a few rules inside.
- **Conventions** - component CSS and colours from the theme are rules for
  people as much as for agents, and belong in documentation people read.
- **Testing** - the two vitest projects and the E2E database rules.

Keep in `CLAUDE.md`: the migration rule and the forbidden `drizzle-kit`
commands, the commands to run, the AI policy pointer, and the short rules that
already point at documents (workflows, links, tasks, tools).

Review the agent's local memory at the same time. Some of what is recorded there
as a personal preference is really a rule for anyone working on this project,
and belongs in the repository instead:

- **Dependencies at their latest stable version, checked against the registry**
  rather than recalled - crates.io, npm, Maven, GitHub releases - and a new
  major version's documentation read before writing code against it.
- **No tools installed on a developer's machine without asking.** Tooling used
  often is installed locally once agreed; a one-off or troubleshooting tool runs
  from a Docker image with `--rm`, and nothing is left running afterwards.
  `CLAUDE.md` has half of this already, under the Dev Requirements rule.

Whatever moves, the result should be checked the way the rest of the
documentation is: lychee for the links, and the claims verified against the
code rather than carried over on trust.
