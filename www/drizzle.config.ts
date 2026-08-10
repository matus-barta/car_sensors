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
	// Pull application tables, but exclude SQLx infrastructure.
	tablesFilter: ['!_sqlx_migrations'],
	verbose: true,
	strict: true
});
