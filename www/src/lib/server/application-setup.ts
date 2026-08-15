import { count } from 'drizzle-orm';
import { db, schema } from '$lib/server/db';

export type ApplicationSetupState = 'required' | 'complete' | 'incomplete';

export async function getApplicationSetupState(): Promise<ApplicationSetupState> {
	const [setup] = await db
		.select({
			completed: schema.applicationSetup.completed
		})
		.from(schema.applicationSetup)
		.limit(1);

	if (!setup) {
		throw new Error('Application setup state is missing.');
	}

	if (setup.completed) {
		return 'complete';
	}

	const [users] = await db
		.select({
			count: count()
		})
		.from(schema.user);

	if (users.count === 0) {
		return 'required';
	}

	return 'incomplete';
}

export async function isApplicationSetupRequired(): Promise<boolean> {
	return (await getApplicationSetupState()) === 'required';
}
