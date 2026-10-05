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
