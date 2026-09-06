import { fileURLToPath } from 'node:url';

import { defineConfig } from 'drizzle-kit';
import { loadEnv } from 'vite';

/*
 * Pointed at the repository root for the same reason as `kit.env.dir` in
 * `vite.config.ts`: there is one environment file and it is not in this
 * directory. Done here rather than in the npm script so that `db:studio` and a
 * bare `drizzle-kit` invocation behave the same way. A real environment
 * variable still wins, which is how CI supplies the database.
 */
const environment = loadEnv('development', fileURLToPath(new URL('..', import.meta.url)), '');

const databaseUrl = process.env.DATABASE_URL ?? environment.DATABASE_URL;

if (!databaseUrl) {
	throw new Error('DATABASE_URL is not set');
}

export default defineConfig({
	dialect: 'postgresql',
	out: './src/lib/server/db/generated',
	dbCredentials: {
		url: databaseUrl
	},
	schemaFilter: ['public'],
	tablesFilter: ['application_setup', 'known_devices', 'telemetry_samples'],
	verbose: true,
	strict: true
});
