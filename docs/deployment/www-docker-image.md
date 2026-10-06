---
title: "Why the web application's Docker image is the size it is"
---

A decision rather than an open question, written down so it is not reopened by
accident. The image is about 355 MB, of which roughly 206 MB is `node_modules`
for five runtime dependencies, and that is being left alone deliberately.

What it costs to leave: a little pull time on the machine that already runs
Postgres, Valkey, pgAdmin and `ingest`, and some registry storage. That is the
whole bill. There is no scale here at which those megabytes matter.

What was tried, so it is not tried again. `pnpm install --prod` in place of
pruning a build stage did help and is what the Dockerfile does. Disabling pnpm's
automatic peer installation is refused outright, because pnpm records the
setting in the lockfile and rejects a frozen install that disagrees - getting
past it means regenerating the lockfile for development and CI too. `pnpm deploy
--prod` needs a workspace with named projects, and `www/pnpm-workspace.yaml`
exists only to carry pnpm settings - `engineStrict` and `allowBuilds` - not to
declare a workspace.

Most of what is left is not ours to remove. `better-auth` is a runtime
dependency declaring `@sveltejs/kit`, `vite` and `vitest` as peers, and pnpm
installs peers, so `typescript`, a `@rolldown` binding and `playwright-core`
arrive with it - around 55 MB the server never opens. `pnpm why --prod` shows
the chain.

The one thing that would genuinely work is bundling the server's dependencies
rather than leaving them external, through `ssr.noExternal`, so that
`node_modules` need not ship at all. It is not worth it. That moves failures to
run time on paths no test covers - `better-auth` loads integrations lazily - and
buys disk on a machine that has plenty. Should this ever be revisited, it wants
a reason better than the size.

Worth separating from all of the above: moving `maplibre-gl` to
`devDependencies` was not an optimisation. The server bundle never referenced
it, because the map imports it dynamically in the browser and the client bundle
carries its own copy; it was simply in the wrong list. The thirty megabytes
were a side effect.
