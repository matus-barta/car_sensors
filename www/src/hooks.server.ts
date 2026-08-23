import type { Handle, HandleServerError } from '@sveltejs/kit';
import { building } from '$app/environment';
import { auth } from '$lib/server/auth';
import { svelteKitHandler } from 'better-auth/svelte-kit';

const AUTH_ROUTE_PREFIX = '/api/auth';

const handleBetterAuth: Handle = async ({ event, resolve }) => {
	/*
	 * Better Auth resolves the session itself while serving its own endpoints,
	 * so looking it up here too would cost a second lookup on every one of
	 * those requests. Nothing outside those routes reads `locals` for them.
	 */
	if (!event.url.pathname.startsWith(AUTH_ROUTE_PREFIX)) {
		const session = await auth.api.getSession({ headers: event.request.headers });

		if (session) {
			event.locals.session = session.session;
			event.locals.user = session.user;
		}
	}

	return svelteKitHandler({ event, resolve, auth, building });
};

export const handle: Handle = handleBetterAuth;

/**
 * Logs unexpected failures with request context.
 *
 * Only the sanitized message reaches the browser; SvelteKit already withholds
 * the underlying error.
 */
export const handleError: HandleServerError = ({ error, event, status, message }) => {
	if (status !== 404) {
		console.error(`Unhandled ${status} on ${event.request.method} ${event.url.pathname}:`, error);
	}

	return {
		message
	};
};
