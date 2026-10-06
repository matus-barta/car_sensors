---
title: "Add a CONTRIBUTING file once someone else contributes"
status: backlog
area: docs
depends_on: []
---

Low priority: nobody outside the project contributes yet, and the README and
the documentation index already lead to everything a contributor needs.

A `CONTRIBUTING.md` is what GitHub
[puts in front of a contributor](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/setting-guidelines-for-repository-contributors):
a link when someone opens an issue or a pull request, a "Contributing" tab
beside the README, and a link in the repository sidebar. It looks in `.github/`,
then the root, then `docs/`.

It would hold no rules of its own - those are in the documentation, and a
second copy would drift - only the way in: [development setup](../development/README.md),
the [conventions](../development/conventions.md),
[writing documentation](../development/writing-documentation.md),
[testing `www`](../development/www/testing.md) and the
[AI usage policy](../development/ai-policy.md), which says what a contribution
made with an AI tool has to meet.

One thing to decide when it is written: in `docs/` it would also become a page
of the documentation site, with a place in the sidebar and the index like any
other; in `.github/` or the root it would stay out of it.
