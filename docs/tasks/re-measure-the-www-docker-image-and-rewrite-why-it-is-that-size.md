---
title: "Re-measure the web application's Docker image and rewrite why it is that size"
status: backlog
area: www
depends_on: []
---

[Why the web application's Docker image is the size it is](../deployment/www-docker-image.md) explains the
size by a dependency chain that no longer exists. `/verify-docs` found, against
the lockfile in October 2026:

- `better-auth` 1.6.25 declares `@sveltejs/kit` and `vitest` as peers, both
  marked optional in `peerDependenciesMeta`, and `vite` not as a peer at all -
  it is one of `better-auth`'s own development dependencies.
- pnpm does not install optional peers by itself, and `pnpm why --prod` for
  `playwright-core`, `typescript` and `vitest` returns nothing in `www/`, while
  it returns a chain for a real production dependency such as `zod`.

So the "around 55 MB the server never opens" that the page attributes to those
peers is probably no longer in the image, and its figures - about 355 MB, of
which 206 MB `node_modules` - were measured before. The comment in
`www/Dockerfile` above the production install repeats the 355 MB.

What to do: build the image, measure it and its `node_modules` (`docker image
ls`, and `du` inside the production stage), see what the largest packages are
and why each is there, and rewrite the page from that. The decision it records
may well still stand - the size is a non-problem on the machine that runs it -
but the reasoning should be what is true now, and the Dockerfile comment should
agree with it.

Worth keeping from the page whatever the numbers say: the paragraph on bundling
with `ssr.noExternal` and why it is not worth it, and the note that
`maplibre-gl` moved to `devDependencies` because it was in the wrong list, not
to save space - both still hold.
