import { test } from '@playwright/test';

import { closeDatabaseConnections, dropTestDatabase } from '../fixtures/database.ts';

test('drop E2E database', async () => {
	try {
		await dropTestDatabase();
	} finally {
		await closeDatabaseConnections();
	}
});
