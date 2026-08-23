import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';
import { loadEnv } from 'vite';

/*
 * `vite preview` runs in production mode and never reads `.env.test`, so the
 * E2E environment is resolved here and handed to the server explicitly. Real
 * environment variables win, which is how CI overrides the database URLs.
 *
 * Anchored to this file rather than the working directory so editors and
 * tooling that load the config from elsewhere still find `.env.test`.
 */
const projectRoot = dirname(fileURLToPath(import.meta.url));

const environment = loadEnv('test', projectRoot, '');

function required(name: string): string {
	const value = process.env[name] ?? environment[name];

	if (!value) {
		throw new Error(
			`${name} is required to run the end-to-end tests. ` +
				'Set it in the environment or in www/.env.test.'
		);
	}

	return value;
}

const baseURL = required('ORIGIN');

export default defineConfig({
	testDir: './e2e',

	fullyParallel: false,
	workers: 1,

	use: {
		baseURL,
		trace: 'on-first-retry',
		screenshot: 'only-on-failure'
	},

	projects: [
		{
			name: 'database setup',
			testMatch: /lifecycle\/database\.setup\.ts/,
			teardown: 'database teardown'
		},
		{
			name: 'chromium',
			testMatch: /\.e2e\.ts/,
			use: {
				...devices['Desktop Chrome']
			},
			dependencies: ['database setup']
		},
		{
			name: 'database teardown',
			testMatch: /lifecycle\/database\.teardown\.ts/
		}
	],

	webServer: {
		command: 'pnpm test:e2e:server',
		url: baseURL,
		// Always start a fresh server so a stale process cannot serve old code.
		reuseExistingServer: false,
		timeout: 120_000,
		env: {
			DATABASE_URL: required('DATABASE_URL'),
			POSTGRES_ADMIN_URL: required('POSTGRES_ADMIN_URL'),
			ORIGIN: baseURL,
			BETTER_AUTH_SECRET: required('BETTER_AUTH_SECRET'),
			PUBLIC_OSM_VECTOR_TILE_URL: required('PUBLIC_OSM_VECTOR_TILE_URL'),
			PUBLIC_OSM_STYLE_URL: required('PUBLIC_OSM_STYLE_URL')
		}
	}
});
