import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import postgres from 'postgres';
import { loadEnv } from 'vite';

const execFileAsync = promisify(execFile);

const environment = loadEnv('test', process.cwd(), '');

const databaseUrl = process.env.DATABASE_URL ?? environment.DATABASE_URL;

const adminDatabaseUrl = process.env.POSTGRES_ADMIN_URL ?? environment.POSTGRES_ADMIN_URL;

if (!databaseUrl) {
	throw new Error('DATABASE_URL is required for end-to-end tests.');
}

if (!adminDatabaseUrl) {
	throw new Error('POSTGRES_ADMIN_URL is required for end-to-end tests.');
}

const parsedDatabaseUrl = new URL(databaseUrl);
const parsedAdminDatabaseUrl = new URL(adminDatabaseUrl);

const databaseName = decodeURIComponent(parsedDatabaseUrl.pathname.replace(/^\//, ''));

const adminDatabaseName = decodeURIComponent(parsedAdminDatabaseUrl.pathname.replace(/^\//, ''));

if (!databaseName.endsWith('_test')) {
	throw new Error(
		`Refusing to use non-test database "${databaseName}". ` +
			'The E2E database name must end with "_test".'
	);
}

if (databaseName === adminDatabaseName) {
	throw new Error(
		'POSTGRES_ADMIN_URL must connect to a different database ' + 'than DATABASE_URL.'
	);
}

const adminSql = postgres(adminDatabaseUrl, {
	max: 1,
	prepare: false
});

let testSql: ReturnType<typeof postgres> | undefined;

function getTestSql(): ReturnType<typeof postgres> {
	testSql ??= postgres(databaseUrl, {
		max: 1,
		prepare: false
	});

	return testSql;
}

/**
 * Creates a fresh E2E database and applies all SQLx migrations.
 *
 * Any existing database with the same name is removed first.
 */
export async function createTestDatabase(): Promise<void> {
	await closeTestDatabaseConnection();

	await terminateTestDatabaseConnections();

	await adminSql.unsafe(`DROP DATABASE IF EXISTS "${escapeIdentifier(databaseName)}"`);

	await adminSql.unsafe(`CREATE DATABASE "${escapeIdentifier(databaseName)}"`);

	await execFileAsync('sqlx', ['migrate', 'run', '--source', '../db/migrations'], {
		cwd: process.cwd(),
		env: {
			...process.env,
			DATABASE_URL: databaseUrl
		}
	});

	await resetDatabase();
}

/**
 * Resets all mutable application and Better Auth data.
 *
 * SQLx migration history is intentionally preserved.
 */
export async function resetDatabase(): Promise<void> {
	const sql = getTestSql();

	await sql.begin(async (transaction) => {
		await transaction`
			TRUNCATE TABLE
				"verification",
				"account",
				"session",
				"user",
				telemetry_samples,
				known_devices,
				application_setup
			RESTART IDENTITY
			CASCADE
		`;

		await transaction`
			INSERT INTO application_setup (
				id,
				completed,
				completed_at
			)
			VALUES (
				'global',
				FALSE,
				NULL
			)
		`;
	});
}

/**
 * Deletes the E2E database after the complete Playwright run.
 */
export async function dropTestDatabase(): Promise<void> {
	await closeTestDatabaseConnection();
	await terminateTestDatabaseConnections();

	await adminSql.unsafe(`DROP DATABASE IF EXISTS "${escapeIdentifier(databaseName)}"`);
}

/**
 * Produces a deliberately incomplete application setup state.
 */
export async function createIncompleteSetupState(): Promise<void> {
	await resetDatabase();

	const sql = getTestSql();

	await sql`
		INSERT INTO "user" (
			id,
			name,
			email,
			email_verified,
			created_at,
			updated_at,
			role,
			banned
		)
		VALUES (
			'e2e-incomplete-setup-user',
			'Test User',
			'incomplete@example.test',
			FALSE,
			NOW(),
			NOW(),
			'user',
			FALSE
		)
	`;
}

export interface ApplicationSetupRecord {
	id: string;
	completed: boolean;
	completedAt: Date | null;
}

export async function getApplicationSetup(): Promise<ApplicationSetupRecord> {
	const sql = getTestSql();

	const [record] = await sql<
		Array<{
			id: string;
			completed: boolean;
			completed_at: Date | null;
		}>
	>`
		SELECT
			id,
			completed,
			completed_at
		FROM application_setup
		WHERE id = 'global'
	`;

	if (!record) {
		throw new Error('Application setup row is missing.');
	}

	return {
		id: record.id,
		completed: record.completed,
		completedAt: record.completed_at
	};
}

export interface TestUserRecord {
	id: string;
	name: string;
	email: string;
	role: string | null;
	banned: boolean | null;
}

export async function getUserByEmail(email: string): Promise<TestUserRecord | null> {
	const sql = getTestSql();

	const [record] = await sql<
		Array<{
			id: string;
			name: string;
			email: string;
			role: string | null;
			banned: boolean | null;
		}>
	>`
		SELECT
			id,
			name,
			email,
			role,
			banned
		FROM "user"
		WHERE email = ${email.toLowerCase()}
		LIMIT 1
	`;

	return record ?? null;
}

export async function getUserCount(): Promise<number> {
	const sql = getTestSql();

	const [record] = await sql<
		Array<{
			count: number;
		}>
	>`
		SELECT COUNT(*)::INTEGER AS count
		FROM "user"
	`;

	return record?.count ?? 0;
}

export async function closeDatabaseConnections(): Promise<void> {
	await closeTestDatabaseConnection();

	await adminSql.end({
		timeout: 5
	});
}

async function closeTestDatabaseConnection(): Promise<void> {
	if (!testSql) {
		return;
	}

	await testSql.end({
		timeout: 5
	});

	testSql = undefined;
}

async function terminateTestDatabaseConnections(): Promise<void> {
	await adminSql`
		SELECT pg_terminate_backend(pid)
		FROM pg_stat_activity
		WHERE datname = ${databaseName}
			AND pid <> pg_backend_pid()
	`;
}

function escapeIdentifier(value: string): string {
	return value.replaceAll('"', '""');
}
export interface TestVehicleInput {
	deviceId: string;
	name: string;
	lastSeenAt?: Date | null;
}

export async function createTestVehicle(input: TestVehicleInput): Promise<void> {
	const sql = getTestSql();

	await sql`
		INSERT INTO known_devices (
			device_id,
			name,
			is_active,
			last_seen_at
		)
		VALUES (
			${input.deviceId},
			${input.name},
			TRUE,
			${input.lastSeenAt ?? null}
		)
	`;
}
export interface TestTelemetryInput {
	deviceId: string;
	id: number;
	timestamp: number;
	latitude: number;
	longitude: number;
	bearing?: number | null;
}

export async function createTestTelemetry(input: TestTelemetryInput): Promise<void> {
	const sql = getTestSql();

	await sql`
		INSERT INTO telemetry_samples (
			device_id,
			id,
			event,
			timestamp,
			latitude,
			longitude,
			bearing
		)
		VALUES (
			${input.deviceId},
			${input.id},
			'location',
			${input.timestamp},
			${input.latitude},
			${input.longitude},
			${input.bearing ?? null}
		)
	`;
}
