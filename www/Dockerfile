# The web application, built once and configured at run time.
#
# Everything it needs to be pointed at a particular deployment - the database,
# the origin, the auth secret, the tile server - is read from the environment
# when the process starts, so one image serves every operator. Nothing here
# bakes in a configuration.

FROM node:24-alpine AS base

# corepack ships with node and pins pnpm to the version in package.json, so the
# image builds with the same one the lockfile was written by.
RUN corepack enable

WORKDIR /build


FROM base AS dependencies

# Copied on their own so a source change does not re-resolve the whole tree.
# `.npmrc` and `pnpm-workspace.yaml` come too: the first pins the engines and
# the second is where the build scripts pnpm is allowed to run are approved,
# and without it a frozen install refuses to proceed.
COPY package.json pnpm-lock.yaml .npmrc pnpm-workspace.yaml ./

RUN pnpm install --frozen-lockfile


FROM dependencies AS builder

COPY . ./

# SvelteKit's post-build analysis imports the server modules to work out which
# routes prerender, and those modules validate their environment at import - so
# the build fails without values it will never use. These are deliberately
# nonsense: nothing connects to them, and this stage is discarded, so they
# cannot become defaults in the published image. The real ones are supplied at
# run time, and `BETTER_AUTH_SECRET` in particular must never acquire a default.
ENV DATABASE_URL=postgres://build:build@127.0.0.1:5432/build-time-placeholder
ENV ORIGIN=http://build-time-placeholder.invalid
ENV BETTER_AUTH_SECRET=build-time-placeholder-not-a-secret

# `svelte-kit sync` runs from `prepare`, which pnpm skips on a frozen install.
RUN pnpm exec svelte-kit sync && pnpm build


# Installed from scratch rather than pruned out of the build stage. `pnpm prune
# --prod` leaves transitive dev packages behind - typescript, playwright and a
# rolldown binary among them, some forty megabytes of things a server never
# runs - where a production-only install simply never fetches them.
FROM base AS production-dependencies

COPY package.json pnpm-lock.yaml .npmrc pnpm-workspace.yaml ./

RUN pnpm install --frozen-lockfile --prod


FROM base AS final

ENV NODE_ENV=production

# Run as somebody other than root. The node image provides this user already.
USER node

WORKDIR /app

COPY --from=builder --chown=node:node /build/build ./build
COPY --from=production-dependencies --chown=node:node /build/node_modules ./node_modules
COPY --from=builder --chown=node:node /build/package.json ./package.json

EXPOSE 3000

# adapter-node reads PORT and HOST, and everything else, from the environment.
ENV PORT=3000
ENV HOST=0.0.0.0

CMD ["node", "build"]
