---
title: "How the web application is put together"
---

The decisions behind the `www` code, and the reasons for them, written down so they are not undone by accident.

## Structure and data

**Server-only code lives in [`$lib/server/`](../../../www/src/lib/server/).** That is the only directory SvelteKit prevents the browser from importing, so all database and auth access belongs there — not in a `server/` folder nested inside a feature directory, which gets no protection.

**Vehicle data reaches the browser through remote functions** ([`$lib/vehicles/vehicle.remote.ts`](../../../www/src/lib/vehicles/vehicle.remote.ts), enabled by `experimental.remoteFunctions`). The returned query is the single source of truth: `VehicleState` wraps it, exposing `.current`/`.loading`/`.error`, and owns the selection plus the derived status. Nothing is selected until the user picks a vehicle: with no selection the map keeps every located vehicle in view, and with one it follows that vehicle. Do not mirror query results into separate `$state` — that was a bug once already. Note that the query reports `loading` during refreshes too, so `VehicleState.loading` gates it on `ready` to keep the background poll from flashing skeletons.

**Anything derived from a timestamp is derived in the browser, against `clock`** ([`$lib/utils/clock.svelte.ts`](../../../www/src/lib/utils/clock.svelte.ts)). A status computed on the server freezes at the value it had when the response was sent, so `VehicleSummary` carries `lastSeenAt` and no status; `VehicleWithStatus` is what the components receive. The clock's interval only runs while an effect is reading it. Freshness of the data itself comes from `pollWhileVisible()` ([`$lib/utils/poll.svelte.ts`](../../../www/src/lib/utils/poll.svelte.ts)) in the app shell, which pauses on a hidden tab; polling faster than 30s is pointless because `ingest` writes each device's `last_seen_at` at most that often - see [what an upload does](../ingest/architecture.md#what-an-upload-does).

**Errors from remote functions must be raised with `error()`.** SvelteKit replaces any other thrown value with a generic `"Internal Error"`, so a plain `throw new Error('...')` silently loses its message. On the client, use `getErrorMessage()` from [`$lib/utils/error`](../../../www/src/lib/utils/error.ts) — SvelteKit's `HttpError` does not extend `Error` and carries its text on `body.message`.

**The app shell mounts only when authenticated** ([`$lib/components/app-shell.svelte`](../../../www/src/lib/components/app-shell.svelte), keyed on user id), so signing out tears the vehicle query down rather than leaving a stale list behind.

**Auth is Better Auth**, served from [`hooks.server.ts`](../../../www/src/hooks.server.ts) rather than through filesystem routing — there is no `src/routes/api/auth/` directory, and there should not be. Use the exported `isAuthPath()` if you need to test whether a request belongs to it.

**Setup state** ([`$lib/server/application-setup.ts`](../../../www/src/lib/server/application-setup.ts)) gates the whole app in the root layout. `finalizeApplicationSetup()` claims a singleton row with a conditional `UPDATE ... WHERE completed = false`, so concurrent callers cannot both win. Better Auth writes accounts on its own connection and therefore cannot join that transaction — account creation is rolled back explicitly if the claim fails.

**shadcn-svelte components in [`$lib/components/ui/`](../../../www/src/lib/components/ui/) are generated.** Add them with the CLI (`pnpm dlx shadcn-svelte@latest add <name>`), which is also the only way to get one back after deletion — the CLI has no remove command. [`components.json`](../../../www/components.json) is a manifest for `add`, not a description of what is installed.

## Styling

**CSS that belongs to one component lives in that component.** `www` styles with Tailwind, so most of the time there is no CSS to place at all - this is not an invitation to move utility classes into `<style>` blocks. It is about the custom CSS that is left over. MapLibre's stylesheet and the hundred-odd lines restyling its controls used to sit in [`src/routes/layout.css`](../../../www/src/routes/layout.css), loaded by every route, to serve one component; they now sit in [`vehicle-map.svelte`](../../../www/src/lib/components/vehicle-map.svelte).

Third-party widgets that build their DOM imperatively cannot be reached by an ordinary scoped selector, since that DOM never gets Svelte's scoping attribute. Anchor a `:global` block under a class on the component's own root instead - `.vehicle-map :global { … }` - which keeps the rules from escaping while still matching. `layout.css` is for what is genuinely global: the theme tokens, the font, and element defaults.

Colours come from the theme tokens in `layout.css`, never from a literal - a rule for every application here, so it is with the [conventions](../conventions.md#colours-come-from-the-theme-never-from-a-literal) rather than on this page.
