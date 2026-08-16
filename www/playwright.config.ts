import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
	testDir: './e2e',

	fullyParallel: false,
	workers: 1,

	use: {
		baseURL: 'http://127.0.0.1:4173',
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
		url: 'http://127.0.0.1:4173',
		reuseExistingServer: false,
		timeout: 120_000,
		env: {
			DATABASE_URL:
				process.env.DATABASE_URL ??
				'postgres://postgres:postgres@127.0.0.1:5432/carsensors_e2e_test',

			POSTGRES_ADMIN_URL:
				process.env.POSTGRES_ADMIN_URL ?? 'postgres://postgres:postgres@127.0.0.1:5432/postgres',

			ORIGIN: process.env.ORIGIN ?? 'http://127.0.0.1:4173',

			BETTER_AUTH_SECRET:
				process.env.BETTER_AUTH_SECRET ?? 'e2e-only-better-auth-secret-not-for-production'
		}
	}
});
