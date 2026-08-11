# Artificial intelligence usage policy

AI tools may be used to assist with research, documentation, analysis, debugging, testing ideas, and code suggestions.

AI is a development tool, not an autonomous contributor, reviewer, maintainer, or decision-maker.

## TLDR

```text
AI may assist with research and prepare suggestions.
A human must understand and review the result.
A human must perform the appropriate validation.
A human must make the final decisions.
A human must create the commit.
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

- Create, amend, or sign commits
- Push branches or tags
- Submit, approve, or merge pull requests
- Create releases or publish packages
- Modify repository settings or branch protections
- Access or modify project secrets
- Apply migrations to shared or production databases
- Deploy changes
- Make final architectural, security, privacy, licensing, or policy decisions

An AI tool may suggest changes or prepare files in a supervised local working tree. A human must review the complete result, select the intended changes, and create the commit personally.

## Required review

Before committing AI-assisted work, the human contributor must:

1. Review the complete diff.
2. Understand every substantive change.
3. Remove unrelated, unnecessary, or fabricated content.
4. Perform the appropriate tests and validation.
5. Review security-sensitive and database-related changes manually.
6. Verify factual claims and external references.
7. Confirm that no secrets, personal data, or confidential information are included.
8. Create the commit personally.

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

## Enforcement

Maintainers may reject contributions that:

- Lack meaningful human review
- Cannot be explained by the submitting contributor
- Contain fabricated APIs, dependencies, references, or test results
- Introduce unexplained or unrelated changes
- Create security, privacy, licensing, or maintainability concerns
- Appear to have been submitted autonomously
