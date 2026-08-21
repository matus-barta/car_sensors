import { describe, expect, it } from 'vitest';

import { calculateVehicleStatus } from './vehicle-status';

describe('calculateVehicleStatus', () => {
	const now = new Date('2026-08-21T12:00:00Z').getTime();

	it('returns online for recent telemetry', () => {
		expect(calculateVehicleStatus('2026-08-21T11:59:00Z', now)).toBe('online');
	});

	it('returns stale for older telemetry', () => {
		expect(calculateVehicleStatus('2026-08-21T11:50:00Z', now)).toBe('stale');
	});

	it('returns offline for expired telemetry', () => {
		expect(calculateVehicleStatus('2026-08-21T11:30:00Z', now)).toBe('offline');
	});

	it('returns offline when telemetry is missing', () => {
		expect(calculateVehicleStatus(null, now)).toBe('offline');
	});
});
