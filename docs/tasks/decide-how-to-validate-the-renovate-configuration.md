---
title: "Decide how to validate the Renovate configuration"
status: backlog
area: ci
depends_on: []
---

`renovate.json` is checked by nothing before Renovate itself reads it. JSON
syntax is easy to confirm, but a misspelt option or a value of the wrong type
parses perfectly well and is only found when Renovate runs - on Monday morning,
going by the schedule - and a rule that silently matches nothing is never found
at all.

Renovate ships a [validator](https://docs.renovatebot.com/config-validation/) for exactly this, `renovate-config-validator`, in the
same npm package as Renovate itself. What needs deciding is where it runs,
because none of the options is free:

- **In CI, on changes to `renovate.json`.** Through `npx --package renovate`,
  which fetches the `renovate` package - 13 MB for the package alone in version
  44.138.0, plus its dependencies - on every run that is not cached. Catches a
  mistake before it merges, which is the point.
- **Locally, as a one-off.** The `renovate/renovate` image is about 380 MB
  compressed for the slim variant and 440 MB for `latest`, which is a lot to pull
  for one check. `npx` is lighter but installs into the npm cache.
- **Not at all, relying on Renovate.** The hosted app reports a configuration it
  cannot use, but only after the change has merged, and it says nothing about a
  rule that is valid but matches nothing.

Worth finding out before choosing: how long `npx --package renovate` takes in a
cold CI run, and whether the validator also flags a `matchFileNames` or
`matchPackageNames` that matches no file or package in the repository - the
kind of mistake most likely here.
