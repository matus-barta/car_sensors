import { createHash } from 'node:crypto';

import { test as base } from '@playwright/test';

export { expect } from '@playwright/test';

/**
 * Playwright's `test`, with every test sending from a client address of its
 * own.
 *
 * Sign-in is rate limited per client address, which Better Auth reads from
 * `X-Forwarded-For`. The preview server has no proxy in front of it, so
 * without the header every request would fall into one shared bucket, and a
 * suite that signs in several times a second would start failing on the limit
 * instead of on what it tests. The address is derived from the test's id, so
 * a retry reuses it and a failure stays reproducible. The range is 198.18/15,
 * set aside for benchmarking, so it can never belong to a real client.
 *
 * The header is added only to requests for the application itself, not through
 * `extraHTTPHeaders`: on a cross-origin request it forces a CORS preflight,
 * which the map's tile server refuses, and the map then fails to load.
 */
export const test = base.extend({
	context: async ({ context, baseURL }, use, testInfo) => {
		const applicationOrigin = new URL(baseURL ?? '').origin;
		const [third = 0, fourth = 0, network = 0] = createHash('sha256')
			.update(testInfo.testId)
			.digest();
		const clientAddress = `198.${18 + (network % 2)}.${third}.${fourth}`;

		await context.route(
			(url) => url.origin === applicationOrigin,
			(route) =>
				route.continue({
					headers: { ...route.request().headers(), 'x-forwarded-for': clientAddress }
				})
		);

		await use(context);
	}
});
