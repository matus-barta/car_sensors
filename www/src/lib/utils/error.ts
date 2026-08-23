/**
 * Extracts a message worth showing to the user from an unknown thrown value.
 *
 * SvelteKit rejects remote functions with an `HttpError`, which carries its
 * text on `body.message` and does not extend `Error`, so reading `.message`
 * alone silently misses everything raised through `error()` on the server.
 */
export function getErrorMessage(cause: unknown, fallback: string): string {
	if (typeof cause !== 'object' || cause === null) {
		return fallback;
	}

	const body = (cause as { body?: unknown }).body;

	if (typeof body === 'object' && body !== null) {
		const bodyMessage = (body as { message?: unknown }).message;

		if (typeof bodyMessage === 'string' && bodyMessage.length > 0) {
			return bodyMessage;
		}
	}

	const message = (cause as { message?: unknown }).message;

	if (typeof message === 'string' && message.length > 0) {
		return message;
	}

	return fallback;
}
