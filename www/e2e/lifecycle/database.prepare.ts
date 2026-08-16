import { closeDatabaseConnections, createTestDatabase } from '../fixtures/database.ts';

try {
	await createTestDatabase();
} finally {
	await closeDatabaseConnections();
}
