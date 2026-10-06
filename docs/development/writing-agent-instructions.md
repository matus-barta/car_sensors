---
title: "Writing instructions for AI agents"
---

Why [`.claude/CLAUDE.md`](../../.claude/CLAUDE.md) is written the way it is, and what to weigh before changing it. It is the file Claude Code reads at the start of every session in this repository - one of the two places a project's file may live, the other being `CLAUDE.md` at the root ([memory documentation](https://code.claude.com/docs/en/memory)).

## Rules there, reasons here

The file holds the rules an agent has to follow, each short, and each linking to the page in `docs/` that gives its reasons. Those pages are written for people, so every rule has a home a person can read, and the file points to it rather than repeating it: one source of truth, and the file stays small.

That shape answers what Anthropic's [best practices](https://code.claude.com/docs/en/best-practices) ask of each line - *"Would removing this cause Claude to make mistakes?"* - and what they would leave out: anything an agent can work out by reading the code, standard conventions, long explanations, file-by-file descriptions, and information that changes often. What they would keep is what stays: commands an agent cannot guess, rules that differ from the defaults, testing instructions, repository etiquette, the project's own architectural decisions, and the gotchas that are not self-evident.

## Why there is no overview of the repository

[Evaluating AGENTS.md](https://arxiv.org/abs/2602.11988) (Gloaguen et al., ETH Zurich) measured coding agents with and without such files. Context files did not generally improve task success and raised inference cost by over 20% on average. Their instructions were followed well, though, and repository overviews were the part that did not help - which is why the file keeps its rules and dropped the table of directories it used to open with. The root [README](../../README.md#repository-layout) has that table, for people. The paper's own conclusion is to measure rather than assume: whether a change to the file helps is worth watching for a while.

## How long it can be

The [memory documentation](https://code.claude.com/docs/en/memory) targets under 200 lines per file: a longer one costs more context and is followed less closely. `@path` imports organise a long file without shortening it, since an imported file is loaded at launch alongside the one that names it. The best practices add that a rule an agent keeps ignoring usually means the file is too long and the rule is getting lost, and that a question it asks although the file answers it means the phrasing is ambiguous. Emphasis such as "IMPORTANT" works on one line; on many, none stands out.

`/doctor prompt-audit`, Claude Code's own audit of its instruction files, reports what has gone stale - instructions written for older models, references to files or commands that no longer exist, files that contradict each other - with proposed edits it applies only when asked ([memory documentation, auditing](https://code.claude.com/docs/en/memory#audit-your-instruction-files)). Worth running after the file has grown, or after a change that moved what it links to.

## What was considered and left for later

**Path-scoped rules.** Files in `.claude/rules/` with `paths:` frontmatter load only when an agent works with matching files, which would keep the `www` rules, the colour rule or the migration rule out of sessions that never touch them. Not used: the file is well under the target, and one file is one place to look.

**`AGENTS.md`.** The tool-neutral form of the same file ([agents.md](https://agents.md/)), read by other coding agents too. Claude Code reads it directly when there is no `CLAUDE.md`, or through an `@AGENTS.md` import from one ([memory documentation, AGENTS.md](https://code.claude.com/docs/en/memory#agents-md)). Moving to it, together with the vendored agent skills, is [a task of its own](../tasks/move-the-agent-instructions-to-agents-md-with-the-skills.md).

## Keeping it true

A rule in the file is only as good as the code still doing what it says. It is checked the way the documentation is - its links by lychee, its claims by `/verify-docs` - and whatever a rule links to is where its reasons change first. rumdl leaves `.claude/` alone, so its style is not linted.
