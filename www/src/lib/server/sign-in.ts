import type { RequestEvent } from '@sveltejs/kit';
import { parseSetCookieHeader, toCookieOptions } from 'better-auth/cookies';

import { auth } from '$lib/server/auth';

export type SignInResult =
	| { outcome: 'signed-in' }
	| { outcome: 'rejected' }
	| { outcome: 'rate-limited'; retryAfterSeconds: number }
	| { outcome: 'failed'; status: number };

/**
 * Signs in with an email and password through Better Auth's request handler.
 *
 * Deliberately not `auth.api.signInEmail()`: Better Auth rate-limits only the
 * requests that pass through its handler, and a direct `auth.api` call skips
 * the limiter entirely. Signing in that way left the login form open to
 * unlimited password guessing while the endpoint it duplicates was limited to
 * three attempts per ten seconds.
 *
 * The incoming request's headers go along, so the limiter sees the client's
 * `X-Forwarded-For` and the origin check sees its `Origin`. The session cookie
 * comes back as `Set-Cookie` on the handler's response - the SvelteKit cookie
 * plugin leaves handler requests alone - and is copied onto the event here.
 */
export async function signInWithEmail(
	event: RequestEvent,
	email: string,
	password: string
): Promise<SignInResult> {
	const headers = new Headers(event.request.headers);
	headers.set('content-type', 'application/json');
	headers.delete('content-length');

	const response = await auth.handler(
		new Request(new URL(`${auth.options.basePath}/sign-in/email`, auth.options.baseURL), {
			method: 'POST',
			headers,
			body: JSON.stringify({ email, password })
		})
	);

	if (response.status === 429) {
		const retryAfter = Number(response.headers.get('x-retry-after'));

		return {
			outcome: 'rate-limited',
			retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 10
		};
	}

	if (response.status >= 400 && response.status < 500) {
		return { outcome: 'rejected' };
	}

	if (!response.ok) {
		return { outcome: 'failed', status: response.status };
	}

	const setCookie = response.headers.get('set-cookie');

	if (setCookie) {
		for (const [name, attributes] of parseSetCookieHeader(setCookie)) {
			event.cookies.set(name, attributes.value, {
				...toCookieOptions(attributes),
				path: attributes.path || '/'
			});
		}
	}

	return { outcome: 'signed-in' };
}
