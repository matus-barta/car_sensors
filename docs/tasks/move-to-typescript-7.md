---
title: "Move to TypeScript 7"
status: blocked
area: ci
depends_on: []
---

Both TypeScript packages, `www` and the documentation site in `docs/starlight/`,
stay on TypeScript 6, and Renovate is told not to offer 7 (`allowedVersions` in
`renovate.json`).

The reason is the type checkers rather than TypeScript itself. In October 2026,
with TypeScript at 7.0.2:

- `svelte-check`, which `pnpm check` runs in `www`, declared support for
  TypeScript 5 and 6 only;
- `typescript-eslint`, which `pnpm lint` runs in `www`, declared support below
  6.1;
- `@astrojs/check`, which `pnpm check` runs in the documentation site, declared
  support for 5 and 6 only.

Moving now would mean type checking either failing or running against a
compiler it does not support, which defeats the point of having it.

**Unblocked when** all three declare support for TypeScript 7 in their peer
dependencies - `npm view <package> peerDependencies.typescript` shows it. Then
remove the `allowedVersions` rule and let Renovate offer the update to both
packages together.

**Check from time to time** - TypeScript majors do not wait for the tools around
them. All three answers have to admit 7:

```bash
npm view svelte-check peerDependencies.typescript       # ^5.0.0 || ^6.0.0 in October 2026
npm view typescript-eslint peerDependencies.typescript  # >=4.8.4 <6.1.0
npm view @astrojs/check peerDependencies.typescript     # ^5.0.0 || ^6.0.0
```

[Check the blocked tasks' conditions automatically](check-the-blocked-tasks-conditions-automatically.md)
would do this without anyone having to remember.
