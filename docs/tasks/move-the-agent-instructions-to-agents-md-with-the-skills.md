---
title: "Move the agent instructions to AGENTS.md, with the skills"
status: backlog
area: docs
depends_on: []
---

The agent instructions are in `.claude/CLAUDE.md`, which only Claude Code
reads, while the [AI usage policy](../development/ai-policy.md) allows any
tool. [`AGENTS.md`](https://agents.md/) is the tool-neutral form of the same
file, read by other coding agents, and Claude Code reads it too: on its own when
there is no `CLAUDE.md`, or through an `@AGENTS.md` import from one
([memory documentation, AGENTS.md](https://code.claude.com/docs/en/memory#agents-md)).
The same page notes that some sessions cannot read `AGENTS.md` directly, which
argues for keeping a `CLAUDE.md` that imports it rather than none.

Done alone, that would leave the skills behind, so it goes together with them.
The vendored skills live in `.agents/skills/` - the `skills` CLI's shared
directory, which it reports as read by Amp, Codex, Cline and others - and are
linked into `.claude/skills/` for Claude Code. This project's own skill,
`verify-docs`, is a real directory in `.claude/skills/` only, so no other tool
sees it. Settling the structure means deciding:

- where `verify-docs` lives, so every tool that reads the instructions can run
  the check they require;
- which agents the CLI links the skills for, now `.agents/skills/` and Claude
  Code only;
- whether the `agents/openai.yaml` files some skills carry, for Codex, stay.

What refers to today's paths and moves with them: `.claude/CLAUDE.md` itself;
[writing instructions for AI agents](../development/writing-agent-instructions.md) and the
[agent skills](../development/README.md#agent-skills) section of development
setup; the exclusions in [`.rumdl.toml`](../../.rumdl.toml) and
[`lychee.toml`](../../lychee.toml); the lychee command in CI and under
[checking each piece](../development/README.md#checking-each-piece), which
names `.claude/CLAUDE.md`; and `verify-docs`'s own scripts, which find the
repository root from where they live.

**Related.** [Review the architecture documentation with the installed skills](review-the-architecture-documentation-with-the-installed-skills.md)
runs the same skills.
