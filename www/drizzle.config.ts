import { defineConfig } from 'drizzle-kit';

if (!process.env.DATABASE_URL) {
	throw new Error('DATABASE_URL is not set');
}

export default defineConfig({
	dialect: 'postgresql',
	out: './src/lib/server/db/generated',
	dbCredentials: {
		url: process.env.DATABASE_URL
	},
	schemaFilter: ['public'],
	tablesFilter: ['application_setup', 'known_devices', 'telemetry_samples'],
	verbose: true,
	strict: true
});
