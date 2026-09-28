import { getRequestEvent } from '$app/server';
import { env } from '$env/dynamic/private';
import { betterAuth } from 'better-auth/minimal';
import { APIError, createAuthMiddleware } from 'better-auth/api';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { admin } from 'better-auth/plugins';
import { sveltekitCookies } from 'better-auth/svelte-kit';

import { AUTH_SETUP_HEADER, AUTH_SETUP_TOKEN } from '$lib/server/auth-bootstrap';
import { db, schema } from '$lib/server/db';
import { MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH } from '$lib/config';

if (!env.ORIGIN) {
	throw new Error('ORIGIN is not set');
}

if (!env.BETTER_AUTH_SECRET) {
	throw new Error('BETTER_AUTH_SECRET is not set');
}

/*
 * The proxies in front of the application, as IP addresses or CIDR ranges.
 * Rate limiting keys on the client's address, which behind a proxy only
 * arrives in `X-Forwarded-For`. Better Auth trusts that header on its own only
 * while it holds a single address; with the proxies listed it strips them from
 * the right and takes the first address that is not one of them.
 *
 * Every proxy in the chain must pass the header on for this to work - a proxy
 * that overwrites it leaves nothing but proxy addresses, and the limit then
 * falls back to one bucket shared by everyone. Invalid entries are logged and
 * ignored by Better Auth.
 */
const trustedProxies = (env.TRUSTED_PROXIES ?? '')
	.split(',')
	.map((entry) => entry.trim())
	.filter(Boolean);

export const auth = betterAuth({
	appName: 'Car Sensors',
	baseURL: env.ORIGIN,
	// Better Auth's default, spelled out so code that addresses the handler directly has one source.
	basePath: '/api/auth',
	secret: env.BETTER_AUTH_SECRET,
	database: drizzleAdapter(db, { provider: 'pg', schema }),
	emailAndPassword: {
		enabled: true,
		minPasswordLength: MIN_PASSWORD_LENGTH,
		maxPasswordLength: MAX_PASSWORD_LENGTH
	},

	advanced: {
		ipAddress: { trustedProxies }
	},

	hooks: {
		before: createAuthMiddleware(async (context) => {
			if (context.path !== '/sign-up/email') {
				return;
			}

			const setupToken = context.headers?.get(AUTH_SETUP_HEADER);

			if (setupToken !== AUTH_SETUP_TOKEN) {
				throw new APIError('FORBIDDEN', {
					message: 'Public account registration is disabled.'
				});
			}
		})
	},

	plugins: [
		admin({
			defaultRole: 'user',
			adminRoles: ['admin']
		}),

		// This must remain the final plugin.
		sveltekitCookies(getRequestEvent)
	]
});
