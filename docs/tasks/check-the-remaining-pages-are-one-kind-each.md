---
title: "Check the remaining pages are one kind each"
status: backlog
area: docs
depends_on: []
---

[The conventions](../development/conventions.md#documentation-one-kind-of-page-at-a-time)
now ask each documentation page to be one kind - a how-to guide, an
explanation or reference - with steps and the reasons for them on separate
pages that link to each other. Only two pages were checked against that when
it was written, and both were split: database migrations from
[database schema ownership](../development/database/ownership.md), and
[working on the Android app](../development/android/README.md) from
[Android build and CI](../development/android/build.md).

The rest have not been read against it. What to look at in each:

- [`docs/deployment/README.md`](../deployment/README.md) - steps to deploy,
  then what the Compose file runs, which may be reference rather than part of
  the how-to.
- [`docs/development/README.md`](../development/README.md) - setting up and
  checking each piece is a how-to; whether the environment section explains
  more than the steps need is the question.
- [`docs/development/ci.md`](../development/ci.md) - an explanation of how the
  workflows are laid out, ending in rules; whether the rules read as what the
  explanation arrives at, or as reference of their own.
- [`docs/overview/android-app.md`](../overview/android-app.md) - what the
  logger does and the platform limitations it lives with; a table of states
  and a list of limitations may be reference inside an explanation.
- [`docs/development/ai-policy.md`](../development/ai-policy.md) - a policy,
  which Diátaxis has no kind for; deciding whether it is reference is enough.
- [`docs/development/conventions.md`](../development/conventions.md) itself,
  which gives each rule with its reasons.

A page may well turn out fine as it is - a sentence of reasoning in a how-to,
or the short list of rules an explanation arrives at, is allowed. A whole
section of the other kind is what calls for a split. Diátaxis
[suggests working one page at a time](https://diataxis.fr/how-to-use-diataxis/)
rather than restructuring in one go, and a page split here goes into the
directory of its sidebar group, the sidebar and `docs/README.md`, as any new
page does.

**Related.** [Give people the rules CLAUDE.md keeps for agents](give-people-the-rules-claude-md-keeps-for-agents.md)
may add documentation-writing rules to the conventions page, which is one of
the pages to check.
