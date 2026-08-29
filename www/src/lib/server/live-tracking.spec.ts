import { describe, expect, it, vi } from 'vitest';

import { parseLiveSample } from './live-tracking';

/*
 * Only `parseLiveSample` is covered here: everything else in this module
 * connects to a real Valkey instance on import, and this project's tests
 * don't take on Redis/Valkey as a dependency to run.
 */
describe('parseLiveSample', () => {
	it('parses a well-formed announcement', () => {
		const sample = parseLiveSample(
			JSON.stringify({
				deviceId: 'car-1',
				timestamp: 1_700_000_000_000,
				latitude: 48.1,
				longitude: 17.1,
				altitude: 200,
				speedKmh: 42,
				bearing: 275,
				accuracyM: 5,
				charging: false,
				powerSource: 'battery'
			})
		);

		expect(sample).toMatchObject({
			deviceId: 'car-1',
			timestamp: 1_700_000_000_000,
			latitude: 48.1,
			longitude: 17.1
		});
	});

	it('accepts an announcement with no optional sensor fields', () => {
		const sample = parseLiveSample(
			JSON.stringify({
				deviceId: 'car-1',
				timestamp: 1_700_000_000_000,
				latitude: 48.1,
				longitude: 17.1
			})
		);

		expect(sample).toMatchObject({
			deviceId: 'car-1',
			latitude: 48.1,
			longitude: 17.1
		});
	});

	it('returns null for a value that is not JSON', () => {
		vi.spyOn(console, 'error').mockImplementation(() => {});

		expect(parseLiveSample('not json')).toBeNull();
	});

	it('returns null when a required field is missing', () => {
		vi.spyOn(console, 'error').mockImplementation(() => {});

		expect(
			parseLiveSample(
				JSON.stringify({
					deviceId: 'car-1',
					timestamp: 1_700_000_000_000
					// latitude/longitude missing
				})
			)
		).toBeNull();
	});

	it('returns null when a field has the wrong type', () => {
		vi.spyOn(console, 'error').mockImplementation(() => {});

		expect(
			parseLiveSample(
				JSON.stringify({
					deviceId: 'car-1',
					timestamp: 1_700_000_000_000,
					latitude: 'not-a-number',
					longitude: 17.1
				})
			)
		).toBeNull();
	});
});
