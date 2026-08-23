import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
	const applicationSetupTable = {
		id: Symbol('applicationSetup.id'),
		completed: Symbol('applicationSetup.completed')
	};

	const userTable = {
		id: Symbol('user.id'),
		role: Symbol('user.role')
	};

	/** Rows returned by `update(...).returning()`, keyed by table. */
	const updateResults = new Map<unknown, unknown[]>();

	const updatedTables: unknown[] = [];

	const limit = vi.fn();
	const from = vi.fn(() => ({ limit }));
	const select = vi.fn(() => ({ from }));

	const deleteWhere = vi.fn(() => Promise.resolve());
	const deleteFrom = vi.fn(() => ({ where: deleteWhere }));

	const transactionClient = {
		update: vi.fn((table: unknown) => {
			updatedTables.push(table);

			return {
				set: () => ({
					where: () => ({
						returning: () => Promise.resolve(updateResults.get(table) ?? [])
					})
				})
			};
		})
	};

	const transaction = vi.fn(
		async (callback: (client: typeof transactionClient) => Promise<unknown>) =>
			callback(transactionClient)
	);

	return {
		applicationSetupTable,
		userTable,
		updateResults,
		updatedTables,
		limit,
		from,
		select,
		deleteFrom,
		deleteWhere,
		transaction,
		transactionClient
	};
});

vi.mock('$lib/server/db', () => ({
	db: {
		select: mocks.select,
		delete: mocks.deleteFrom,
		transaction: mocks.transaction
	},
	schema: {
		applicationSetup: mocks.applicationSetupTable,
		user: mocks.userTable
	}
}));

import {
	deleteSetupUser,
	finalizeApplicationSetup,
	getApplicationSetupState,
	isApplicationSetupRequired
} from './application-setup';

beforeEach(() => {
	vi.clearAllMocks();

	mocks.updateResults.clear();
	mocks.updatedTables.length = 0;
});

describe('getApplicationSetupState', () => {
	it('returns required when no account exists yet', async () => {
		mocks.limit.mockResolvedValue([{ completed: false, userCount: 0 }]);

		await expect(getApplicationSetupState()).resolves.toBe('required');
	});

	it('reopens setup when the row claims completion but every account is gone', async () => {
		mocks.limit.mockResolvedValue([{ completed: true, userCount: 0 }]);

		await expect(getApplicationSetupState()).resolves.toBe('required');
	});

	it('returns complete when setup is marked completed and an account exists', async () => {
		mocks.limit.mockResolvedValue([{ completed: true, userCount: 1 }]);

		await expect(getApplicationSetupState()).resolves.toBe('complete');
	});

	it('returns incomplete when an account exists but setup was never finalized', async () => {
		mocks.limit.mockResolvedValue([{ completed: false, userCount: 1 }]);

		await expect(getApplicationSetupState()).resolves.toBe('incomplete');
	});

	it('reads the state in a single query', async () => {
		mocks.limit.mockResolvedValue([{ completed: false, userCount: 1 }]);

		await getApplicationSetupState();

		expect(mocks.select).toHaveBeenCalledOnce();
		expect(mocks.from).toHaveBeenCalledOnce();
		expect(mocks.from).toHaveBeenCalledWith(mocks.applicationSetupTable);
	});

	it('throws when the application setup row is missing', async () => {
		mocks.limit.mockResolvedValue([]);

		await expect(getApplicationSetupState()).rejects.toThrow('Application setup state is missing.');
	});

	it('propagates database errors while reading setup state', async () => {
		mocks.limit.mockRejectedValue(new Error('Database unavailable'));

		await expect(getApplicationSetupState()).rejects.toThrow('Database unavailable');
	});
});

describe('isApplicationSetupRequired', () => {
	it('returns true when setup is required', async () => {
		mocks.limit.mockResolvedValue([{ completed: false, userCount: 0 }]);

		await expect(isApplicationSetupRequired()).resolves.toBe(true);
	});

	it('returns false when setup is complete', async () => {
		mocks.limit.mockResolvedValue([{ completed: true, userCount: 1 }]);

		await expect(isApplicationSetupRequired()).resolves.toBe(false);
	});

	it('returns false when setup is incomplete and awaiting recovery', async () => {
		mocks.limit.mockResolvedValue([{ completed: false, userCount: 1 }]);

		await expect(isApplicationSetupRequired()).resolves.toBe(false);
	});
});

describe('finalizeApplicationSetup', () => {
	it('claims the setup row and promotes the account in one transaction', async () => {
		mocks.updateResults.set(mocks.applicationSetupTable, [{ id: 'global' }]);
		mocks.updateResults.set(mocks.userTable, [{ id: 'user-1' }]);

		await expect(finalizeApplicationSetup('user-1')).resolves.toBe(true);

		expect(mocks.transaction).toHaveBeenCalledOnce();

		expect(mocks.updatedTables).toEqual([mocks.applicationSetupTable, mocks.userTable]);
	});

	it('returns false and leaves the account untouched when another caller claimed setup', async () => {
		mocks.updateResults.set(mocks.applicationSetupTable, []);

		await expect(finalizeApplicationSetup('user-1')).resolves.toBe(false);

		expect(mocks.updatedTables).toEqual([mocks.applicationSetupTable]);
	});

	it('throws when the administrator role could not be assigned', async () => {
		mocks.updateResults.set(mocks.applicationSetupTable, [{ id: 'global' }]);
		mocks.updateResults.set(mocks.userTable, []);

		await expect(finalizeApplicationSetup('user-1')).rejects.toThrow(
			'The initial administrator could not be assigned.'
		);
	});
});

describe('deleteSetupUser', () => {
	it('deletes the account so the installation returns to a clean state', async () => {
		await deleteSetupUser('user-1');

		expect(mocks.deleteFrom).toHaveBeenCalledOnce();
		expect(mocks.deleteFrom).toHaveBeenCalledWith(mocks.userTable);
		expect(mocks.deleteWhere).toHaveBeenCalledOnce();
	});
});
