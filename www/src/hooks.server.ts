import type { Handle, HandleServerError } from '@sveltejs/kit';
import { building } from '$app/environment';
import { auth } from '$lib/server/auth';
import { isAuthPath, svelteKitHandler } from 'better-auth/svelte-kit';

const handleBetterAuth: Handle = async ({ event, resolve }) => {
	/*
	 * Better Auth serves its own endpoints from this hook rather than through
	 * SvelteKit routing, and resolves the session itself while doing so, which
	 * is why looking it up here as well would cost a second lookup on every one
	 * of those requests. Nothing outside those routes reads `locals` for them.
	 *
	 * `isAuthPath` is the same predicate `svelteKitHandler` uses below, so the
	 * two cannot drift if `basePath` or `baseURL` is ever configured.
	 */
	if (!isAuthPath(event.url.toString(), auth.options)) {
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
