import { test } from '@playwright/test';

import { closeDatabaseConnections, resetDatabase } from '../fixtures/database.ts';

test('reset E2E database', async () => {
	try {
		await resetDatabase();
	} finally {
		await closeDatabaseConnections();
	}
});
