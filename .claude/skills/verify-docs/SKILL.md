---
name: verify-docs
description: Verify the factual claims in this repository's documentation against the code, citing evidence that a script then checks. Use when asked to verify, audit or fact-check anything in docs/, a README, CLAUDE.md or a task, or after writing or changing documentation.
---

# Verify documentation claims

Documentation here is largely written with AI help, and a sentence can read
perfectly and still be wrong about the code. This skill checks each factual
claim against the code itself and cites the evidence; a script then confirms
every cited quote really exists, so a verdict cannot rest on a misremembered
file.

## 1. Choose what to verify

The files named in the request. Without any, the Markdown changed on this branch
and in the working tree:

```bash
git diff --name-only main...HEAD -- '*.md'; git diff --name-only -- '*.md'
```

Skip generated output - `docs/schema/` and `docs/api/openapi.json` - which is
correct by construction and checked for drift in CI. One report per document.

## 2. List the claims

Read the whole document. A claim is a statement about this repository that is
either true or false: a file, function, type, column, option or command that
exists; a number - a limit, timeout, version, count; a behaviour - "X calls Y",
"fails when", "only on pull requests". Record each with its line number, one
checkable fact per claim.

Not claims, so leave them out: reasoning, intent, opinion, history ("this used
to..."), and advice. Statements about the outside world - another project's
behaviour, a version that exists upstream - are claims, but check them only if a
registry or the project's own source answers cheaply; otherwise mark them
unverifiable and say so. Such a claim should link its primary source - the
issue, the policy, the reference documentation - so it can be checked again
later. Check it there, and where the document has no link, propose adding one
along with the fix.

## 3. Check each claim against the code

Find the code that decides it - grep, then read the file - and give a verdict:

- **confirmed** - the code does what the claim says;
- **contradicted** - the code does something else;
- **unverifiable** - nothing in the repository settles it.

Evidence comes from code, configuration and generated output. **Other prose is
not evidence**: not another document, not `CLAUDE.md`, not a code comment - a
comment is only evidence of what the comment says. Never decide from memory, and
never from what the document under review says elsewhere.

## 4. Cite the evidence

Every confirmed or contradicted claim cites at least one verbatim quote, a few
lines at most, with the file and the first and last line it spans. For a
contradicted claim, quote what the code actually says and explain the difference
in `note`. Write the report as JSON to a scratch location, not into the
repository - the format is in the docstring of `scripts/check_evidence.py`.

## 5. Check the evidence

```bash
python3 .claude/skills/verify-docs/scripts/check_evidence.py REPORT.json
```

A failing quote means the citation is wrong, not the script. Re-read the file
and correct the citation, or downgrade the claim to unverifiable. Never edit a
quote to make it pass without reading the lines again.

## 6. Report

Lead with the contradicted claims - line, claim, what the code says, evidence -
then the unverifiable ones and why, then the count confirmed. Propose an edit
for each contradicted claim, but change the document only when asked. Where a
claim keeps needing checking by hand, say whether it could be generated or
tested instead; that is what keeps it true afterwards.
