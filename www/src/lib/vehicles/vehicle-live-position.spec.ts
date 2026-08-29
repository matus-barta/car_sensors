import { describe, expect, it } from 'vitest';

import { mergeLivePosition } from './vehicle-live-position';

import type { VehicleSummary } from './vehicle';

describe('mergeLivePosition', () => {
	const vehicle: VehicleSummary = {
		id: 'device-1',
		name: 'Škoda Octavia',
		lastSeenAt: '2026-08-21T11:00:00Z',
		latitude: 48.1,
		longitude: 17.1,
		bearing: 90
	};

	it('returns the summary untouched when there is no live position', () => {
		expect(mergeLivePosition(vehicle, null)).toEqual(vehicle);
	});

	it('overlays the live position onto the summary', () => {
		const merged = mergeLivePosition(vehicle, {
			lastSeenAt: '2026-08-21T12:00:00Z',
			latitude: 48.2,
			longitude: 17.2,
			bearing: 180
		});

		expect(merged).toEqual({
			id: 'device-1',
			name: 'Škoda Octavia',
			lastSeenAt: '2026-08-21T12:00:00Z',
			latitude: 48.2,
			longitude: 17.2,
			bearing: 180
		});
	});

	it('carries a null bearing from the live position through', () => {
		const merged = mergeLivePosition(vehicle, {
			lastSeenAt: '2026-08-21T12:00:00Z',
			latitude: 48.2,
			longitude: 17.2,
			bearing: null
		});

		expect(merged.bearing).toBeNull();
	});
});
