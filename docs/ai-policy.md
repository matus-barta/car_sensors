---
title: "Artificial intelligence usage policy"
---

AI tools may be used to assist with research, documentation, analysis, debugging, testing ideas, and code suggestions.

AI is a development tool, not an autonomous contributor, reviewer, maintainer, or decision-maker.

## TLDR

```text
AI may assist with research and prepare suggestions.
A human must understand and review the result.
A human must perform the appropriate validation.
A human must make the final decisions.
AI may prepare commits; a human reviews every changed line before it is pushed.
A human remains responsible for the contribution.
```

## Human responsibility

All AI-assisted work must be meaningfully reviewed and understood by a human before it is included in the repository.

The human contributor remains fully responsible for:

- Correctness and maintainability
- Security and privacy
- Testing and validation
- Architectural decisions
- Licensing and attribution
- Database migrations
- The complete contents of the submitted change

AI-generated output must not be accepted only because it appears plausible, compiles, or passes automated checks.

The human contributor must be able to explain every substantive part of the change.

## Repository access

AI agents must not operate on the repository without human supervision.

AI agents are not permitted to:

- Push branches or tags
- Submit, approve, or merge pull requests
- Create releases or publish packages
- Modify repository settings or branch protections
- Access or modify project secrets
- Apply migrations to shared or production databases
- Deploy changes
- Make final architectural, security, privacy, licensing, or policy decisions
- Act in the project's shared spaces - issues, pull requests, reviews, discussions - without a human approving each action
- Decide whether a contribution is accepted: an AI tool may help a reviewer, but the decision is a human's

An AI tool may suggest changes and prepare files in a supervised local working tree and, when the developer asks, create the commits and write their messages. However a commit was made, the developer reviews every changed line of it before it is pushed - nothing reaches the shared repository unreviewed - and it is authored under the developer's name.

## Required review

Before pushing AI-assisted work, the human contributor must:

1. Review every changed line of every commit, including commits an AI tool created.
2. Understand every substantive change.
3. Remove unrelated, unnecessary, or fabricated content.
4. Perform the appropriate tests and validation.
5. Review security-sensitive and database-related changes manually.
6. Verify factual claims and external references.
7. Confirm that no secrets, personal data, or confidential information are included.
8. Push only what has been reviewed.

Automated checks assist human review but do not replace it.

## Sensitive information

Do not provide AI services with:

- Passwords, tokens, API keys, or private keys
- Repository or deployment secrets
- Production environment files
- Production database contents
- Personal information
- Location histories or private telemetry
- Confidential logs or proprietary material

Use sanitized examples when requesting AI assistance.

## Copyright and licensing

AI-generated or AI-suggested material must not be included when its origin, licensing status, or compatibility with the project license cannot be reasonably established.

The human contributor must ensure that submitted work:

- Can legally be distributed
- Is compatible with the project's `AGPL-3.0-only` license
- Includes any required attribution
- Does not reproduce copyrighted or incompatibly licensed material

## Disclosure

Material use of AI should be disclosed in a pull request when it would help reviewers assess the contribution.

Example:

```text
AI assistance was used to research and draft parts of this change.

The resulting work was reviewed and validated by the human contributor,
who understands the changes and accepts responsibility for them.
```

Minor assistance such as spelling correction, formatting, or simple editor completion does not require disclosure.

A commit an AI tool helped to create should say so in an `Assisted-by:` trailer naming the tool, for example `Assisted-by: Claude Code (Claude Opus 5.5)`. That records the assistance, for transparency, and nothing more: the tool is not an author and the work is not its own. Do not use `Co-authored-by` for a tool - GitHub treats that trailer as naming a co-author and credits the commit to them. The developer is the author, answers for every line, and must be able to explain it.

## Sources

This policy follows what projects that allow AI assistance, under human
responsibility, have settled on: the
[Linux kernel](https://github.com/torvalds/linux/blob/master/Documentation/process/coding-assistants.rst)
(the `Assisted-by:` tag, and only humans certifying the contribution),
[LLVM](https://llvm.org/docs/AIToolPolicy.html) (a human in the loop, and no
agents acting without approval), [Fedora](https://communityblog.fedoraproject.org/council-policy-proposal-policy-on-ai-assisted-contributions/)
(the contributor as author, and AI never making the final call on a review),
[OpenInfra](https://openinfra.org/legal/ai-policy/) and the
[Apache Software Foundation](https://www.apache.org/legal/generative-tooling.html).
GitHub's documentation on
[commits with multiple authors](https://docs.github.com/en/pull-requests/committing-changes-to-your-project/creating-and-editing-commits/creating-a-commit-with-multiple-authors)
is why `Co-authored-by` is not used for a tool.

## Enforcement

Maintainers may reject contributions that:

- Lack meaningful human review
- Cannot be explained by the submitting contributor
- Contain fabricated APIs, dependencies, references, or test results
- Introduce unexplained or unrelated changes
- Create security, privacy, licensing, or maintainability concerns
- Appear to have been submitted autonomously
