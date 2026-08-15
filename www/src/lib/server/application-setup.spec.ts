import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
	const applicationSetupTable = {
		completed: Symbol('applicationSetup.completed')
	};

	const userTable = Symbol('user');

	const limit = vi.fn();
	const from = vi.fn();
	const select = vi.fn();

	return {
		applicationSetupTable,
		userTable,
		limit,
		from,
		select
	};
});

vi.mock('$lib/server/db', () => ({
	db: {
		select: mocks.select
	},
	schema: {
		applicationSetup: mocks.applicationSetupTable,
		user: mocks.userTable
	}
}));

import { getApplicationSetupState, isApplicationSetupRequired } from './application-setup';

describe('getApplicationSetupState', () => {
	beforeEach(() => {
		vi.clearAllMocks();

		mocks.select.mockImplementation(() => ({
			from: mocks.from
		}));

		mocks.from.mockImplementation((table) => {
			if (table === mocks.applicationSetupTable) {
				return {
					limit: mocks.limit
				};
			}

			if (table === mocks.userTable) {
				return Promise.resolve([
					{
						count: 0
					}
				]);
			}

			throw new Error('Unexpected table');
		});
	});

	it('returns required when setup is incomplete and no users exist', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: false
			}
		]);

		await expect(getApplicationSetupState()).resolves.toBe('required');

		expect(mocks.from).toHaveBeenCalledWith(mocks.applicationSetupTable);

		expect(mocks.from).toHaveBeenCalledWith(mocks.userTable);
	});

	it('returns complete when setup is marked completed', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: true
			}
		]);

		await expect(getApplicationSetupState()).resolves.toBe('complete');

		expect(mocks.from).toHaveBeenCalledOnce();
		expect(mocks.from).toHaveBeenCalledWith(mocks.applicationSetupTable);
	});

	it('returns incomplete when setup is unfinished but a user exists', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: false
			}
		]);

		mocks.from.mockImplementation((table) => {
			if (table === mocks.applicationSetupTable) {
				return {
					limit: mocks.limit
				};
			}

			if (table === mocks.userTable) {
				return Promise.resolve([
					{
						count: 1
					}
				]);
			}

			throw new Error('Unexpected table');
		});

		await expect(getApplicationSetupState()).resolves.toBe('incomplete');
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
	beforeEach(() => {
		vi.clearAllMocks();

		mocks.select.mockImplementation(() => ({
			from: mocks.from
		}));

		mocks.from.mockImplementation((table) => {
			if (table === mocks.applicationSetupTable) {
				return {
					limit: mocks.limit
				};
			}

			if (table === mocks.userTable) {
				return Promise.resolve([
					{
						count: 0
					}
				]);
			}

			throw new Error('Unexpected table');
		});
	});

	it('returns true when setup is required', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: false
			}
		]);

		await expect(isApplicationSetupRequired()).resolves.toBe(true);
	});

	it('returns false when setup is complete', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: true
			}
		]);

		await expect(isApplicationSetupRequired()).resolves.toBe(false);
	});

	it('returns false when setup is incomplete and requires recovery', async () => {
		mocks.limit.mockResolvedValue([
			{
				completed: false
			}
		]);

		mocks.from.mockImplementation((table) => {
			if (table === mocks.applicationSetupTable) {
				return {
					limit: mocks.limit
				};
			}

			if (table === mocks.userTable) {
				return Promise.resolve([
					{
						count: 1
					}
				]);
			}

			throw new Error('Unexpected table');
		});

		await expect(isApplicationSetupRequired()).resolves.toBe(false);
	});
});
