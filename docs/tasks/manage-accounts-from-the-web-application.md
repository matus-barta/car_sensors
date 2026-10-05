---
title: "Manage accounts from the web application"
status: backlog
area: www
depends_on: []
---

The application has exactly one way to create an account: the setup screen,
which makes the first one and gives it the `admin` role. Public sign-up is
refused - `src/lib/server/auth.ts` rejects `/sign-up/email` unless the request
carries the internal setup token - so after setup nobody else can get in.

Better Auth's admin plugin is already enabled in `auth.ts`, with
`defaultRole: 'user'` and `adminRoles: ['admin']`, which gives an admin session
endpoints for creating, listing, banning and removing users and for changing
roles. Nothing uses them: there is no page for it under `src/routes/`, and no
Better Auth client is created in the browser at all. The `www` README used to
describe "an administrator workflow" for further accounts; it now says there is
none yet and points here.

What it would take: an admin-only page listing the accounts, with creating one,
disabling or removing one, and changing a role - built on the plugin's endpoints
rather than on the user table directly, so Better Auth stays the only writer of
its own tables. Signing out a removed user's sessions is part of removing them.

Worth deciding when it is written: how a new user gets their first password.
There is no email service, so an invitation link cannot be sent; an admin
setting an initial password, which the user is made to change on first sign-in,
is the likely answer. And whether an admin may remove the last admin, which
would lock everyone out.
