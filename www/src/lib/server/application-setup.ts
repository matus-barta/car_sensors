import { count, eq, sql } from 'drizzle-orm';

import { db, schema } from '$lib/server/db';

export async function isApplicationSetupRequired(): Promise<boolean> {
	const [result] = await db
		.select({
			userCount: count()
		})
		.from(schema.user);

	return result.userCount === 0;
}

export async function withApplicationSetupLock<T>(
	callback: (transaction: Parameters<Parameters<typeof db.transaction>[0]>[0]) => Promise<T>
): Promise<T> {
	return db.transaction(async (transaction) => {
		const rows = await transaction.execute<{
			completed: boolean;
		}>(
			sql`
				SELECT completed
				FROM application_setup
				WHERE id = 'global'
				FOR UPDATE
			`
		);

		const setup = rows[0];

		if (!setup) {
			throw new Error('Application setup state is missing.');
		}

		if (setup.completed) {
			throw new Error('Application setup has already been completed.');
		}

		const [userCount] = await transaction
			.select({
				value: count()
			})
			.from(schema.user);

		if (userCount.value > 0) {
			await transaction
				.update(schema.applicationSetup)
				.set({
					completed: true,
					completedAt: sql`now()`
				})
				.where(eq(schema.applicationSetup.id, 'global'));

			throw new Error('Application setup has already been completed.');
		}

		return callback(transaction);
	});
}
