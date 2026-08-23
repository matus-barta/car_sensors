import { and, eq, sql } from 'drizzle-orm';
import { db, schema } from '$lib/server/db';

export type ApplicationSetupState = 'required' | 'complete' | 'incomplete';

/**
 * Reports whether the installation still needs its initial administrator.
 *
 * `incomplete` means accounts exist but setup was never finalized. That state
 * is recoverable: see `finalizeApplicationSetup`.
 */
export async function getApplicationSetupState(): Promise<ApplicationSetupState> {
	const [setup] = await db
		.select({
			completed: schema.applicationSetup.completed,
			userCount: sql<number>`(SELECT count(*)::int FROM ${schema.user})`
		})
		.from(schema.applicationSetup)
		.limit(1);

	if (!setup) {
		throw new Error('Application setup state is missing.');
	}

	// Without an account there is no way to sign in, so setup must stay open
	// even if the row claims otherwise.
	if (setup.userCount === 0) {
		return 'required';
	}

	if (setup.completed) {
		return 'complete';
	}

	return 'incomplete';
}

export async function isApplicationSetupRequired(): Promise<boolean> {
	return (await getApplicationSetupState()) === 'required';
}

/**
 * Claims the singleton setup row and promotes `userId` to administrator.
 *
 * Both statements run in one transaction, and the claim only succeeds while
 * `completed` is still false, so concurrent callers cannot both win.
 *
 * Returns `false` when another caller finalized setup first.
 */
export async function finalizeApplicationSetup(userId: string): Promise<boolean> {
	return db.transaction(async (transaction) => {
		const claimed = await transaction
			.update(schema.applicationSetup)
			.set({
				completed: true,
				completedAt: sql`now()`
			})
			.where(
				and(eq(schema.applicationSetup.id, 'global'), eq(schema.applicationSetup.completed, false))
			)
			.returning({
				id: schema.applicationSetup.id
			});

		if (claimed.length === 0) {
			return false;
		}

		const promoted = await transaction
			.update(schema.user)
			.set({
				role: 'admin'
			})
			.where(eq(schema.user.id, userId))
			.returning({
				id: schema.user.id
			});

		if (promoted.length !== 1) {
			throw new Error('The initial administrator could not be assigned.');
		}

		return true;
	});
}

/**
 * Removes an account created by a setup attempt that could not be finalized.
 *
 * Sessions and credentials cascade from the user row, so this returns the
 * installation to a clean `required` state.
 */
export async function deleteSetupUser(userId: string): Promise<void> {
	await db.delete(schema.user).where(eq(schema.user.id, userId));
}
