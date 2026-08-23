import { describe, expect, it } from 'vitest';

import { getErrorMessage } from './error';

const FALLBACK = 'Something went wrong.';

describe('getErrorMessage', () => {
	it('reads the message of a regular error', () => {
		expect(getErrorMessage(new Error('Boom'), FALLBACK)).toBe('Boom');
	});

	it("reads an HttpError's body message", () => {
		// SvelteKit's HttpError does not extend Error and carries its text on `body`.
		const httpError = {
			status: 409,
			body: {
				message: 'A vehicle with this device ID already exists.'
			}
		};

		expect(getErrorMessage(httpError, FALLBACK)).toBe(
			'A vehicle with this device ID already exists.'
		);
	});

	it('prefers the body message over a top-level message', () => {
		const error = {
			message: 'Internal Error',
			body: {
				message: 'The device is already registered.'
			}
		};

		expect(getErrorMessage(error, FALLBACK)).toBe('The device is already registered.');
	});

	it('falls back for a plain string', () => {
		expect(getErrorMessage('nope', FALLBACK)).toBe(FALLBACK);
	});

	it('falls back for null', () => {
		expect(getErrorMessage(null, FALLBACK)).toBe(FALLBACK);
	});

	it('falls back for an empty message', () => {
		expect(getErrorMessage(new Error(''), FALLBACK)).toBe(FALLBACK);
	});
});
