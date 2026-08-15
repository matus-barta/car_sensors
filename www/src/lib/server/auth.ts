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

export const auth = betterAuth({
	appName: 'Car Sensors',
	baseURL: env.ORIGIN,
	secret: env.BETTER_AUTH_SECRET,
	database: drizzleAdapter(db, { provider: 'pg', schema }),
	emailAndPassword: {
		enabled: true,
		minPasswordLength: MIN_PASSWORD_LENGTH,
		maxPasswordLength: MAX_PASSWORD_LENGTH
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
