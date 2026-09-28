import { describe, expect, it } from 'vitest';

import {
	frameFleet,
	hasCoordinates,
	planCamera,
	VEHICLE_ZOOM,
	type CameraState,
	type MaybeLocated
} from './vehicle-camera';

const alpha: MaybeLocated = { id: 'alpha', latitude: null, longitude: null };
const bravo: MaybeLocated = { id: 'bravo', latitude: 48.1486, longitude: 17.1077 };
const charlie: MaybeLocated = { id: 'charlie', latitude: 48.2, longitude: 17.2 };

// The key `planCamera()` gives for this set, framed in the order it sorts them.
const bravoAndCharlie = 'bravo\ncharlie';

function state(overrides: Partial<CameraState<MaybeLocated>>): CameraState<MaybeLocated> {
	return {
		vehicles: [alpha, bravo, charlie],
		selectedVehicleId: null,
		following: true,
		framedFleetKey: null,
		isNearViewEdge: () => false,
		...overrides
	};
}

describe('hasCoordinates', () => {
	it('accepts a position on the map', () => {
		expect(hasCoordinates(bravo)).toBe(true);
	});

	it.each([
		['no position', { id: 'x', latitude: null, longitude: null }],
		['a missing longitude', { id: 'x', latitude: 48 }],
		['a latitude past the pole', { id: 'x', latitude: 91, longitude: 17 }],
		['a longitude past the antimeridian', { id: 'x', latitude: 48, longitude: 181 }],
		['a value that is not a number', { id: 'x', latitude: Number.NaN, longitude: 17 }]
	])('rejects %s', (_, vehicle) => {
		expect(hasCoordinates(vehicle)).toBe(false);
	});
});

describe('planCamera with nothing selected', () => {
	it('frames every located vehicle the first time', () => {
		expect(planCamera(state({}))).toEqual({
			kind: 'frame',
			fleetKey: bravoAndCharlie,
			view: {
				kind: 'bounds',
				bounds: [
					[17.1077, 48.1486],
					[17.2, 48.2]
				],
				maxZoom: VEHICLE_ZOOM
			}
		});
	});

	it('leaves vehicles without a position out of the frame', () => {
		const plan = planCamera(state({ vehicles: [alpha, bravo] }));

		expect(plan).toEqual({
			kind: 'frame',
			fleetKey: 'bravo',
			view: { kind: 'center', center: [17.1077, 48.1486], zoom: VEHICLE_ZOOM }
		});
	});

	it('stays when no vehicle has a position', () => {
		expect(planCamera(state({ vehicles: [alpha] }))).toEqual({ kind: 'stay' });
	});

	it('stays while the same vehicles move about inside the view', () => {
		expect(planCamera(state({ framedFleetKey: bravoAndCharlie }))).toEqual({ kind: 'stay' });
	});

	it('frames again when a vehicle reaches the edge of the view', () => {
		const plan = planCamera(
			state({
				framedFleetKey: bravoAndCharlie,
				isNearViewEdge: (vehicle) => vehicle.id === 'charlie'
			})
		);

		expect(plan.kind).toBe('frame');
	});

	it('frames again when a vehicle reports its first position', () => {
		const located = { ...alpha, latitude: 48.3, longitude: 17.3 };

		const plan = planCamera(
			state({ vehicles: [located, bravo, charlie], framedFleetKey: bravoAndCharlie })
		);

		expect(plan).toMatchObject({ kind: 'frame', fleetKey: 'alpha\nbravo\ncharlie' });
	});

	it('frames again when a vehicle leaves', () => {
		const plan = planCamera(state({ vehicles: [bravo], framedFleetKey: bravoAndCharlie }));

		expect(plan).toMatchObject({ kind: 'frame', fleetKey: 'bravo' });
	});

	it('does not ask about the edge for a vehicle without a position', () => {
		const asked: string[] = [];

		planCamera(
			state({
				framedFleetKey: bravoAndCharlie,
				isNearViewEdge: (vehicle) => {
					asked.push(vehicle.id);

					return false;
				}
			})
		);

		expect(asked).not.toContain('alpha');
	});

	it('stays where the user moved it, even for a new vehicle or one at the edge', () => {
		expect(
			planCamera(
				state({
					following: false,
					framedFleetKey: 'bravo',
					isNearViewEdge: () => true
				})
			)
		).toEqual({ kind: 'stay' });
	});
});

describe('planCamera with a vehicle selected', () => {
	it('follows the selected vehicle', () => {
		expect(planCamera(state({ selectedVehicleId: 'charlie' }))).toEqual({
			kind: 'follow',
			center: [17.2, 48.2]
		});
	});

	it('follows it on every update, wherever it is in the view', () => {
		expect(
			planCamera(state({ selectedVehicleId: 'charlie', framedFleetKey: bravoAndCharlie }))
		).toEqual({ kind: 'follow', center: [17.2, 48.2] });
	});

	it('stays while the selected vehicle has no position', () => {
		expect(planCamera(state({ selectedVehicleId: 'alpha' }))).toEqual({ kind: 'stay' });
	});

	it('stays when the selected vehicle is not in the list', () => {
		expect(planCamera(state({ selectedVehicleId: 'gone' }))).toEqual({ kind: 'stay' });
	});

	it('stays where the user moved it', () => {
		expect(planCamera(state({ selectedVehicleId: 'charlie', following: false }))).toEqual({
			kind: 'stay'
		});
	});
});

describe('frameFleet', () => {
	it('shows a single vehicle at street level', () => {
		expect(frameFleet([{ id: 'bravo', latitude: 48.1486, longitude: 17.1077 }])).toEqual({
			kind: 'center',
			center: [17.1077, 48.1486],
			zoom: VEHICLE_ZOOM
		});
	});

	it('frames several vehicles by the box around them', () => {
		expect(
			frameFleet([
				{ id: 'a', latitude: 48.2, longitude: 17.1 },
				{ id: 'b', latitude: 48.1, longitude: 17.3 },
				{ id: 'c', latitude: 48.15, longitude: 17.2 }
			])
		).toEqual({
			kind: 'bounds',
			bounds: [
				[17.1, 48.1],
				[17.3, 48.2]
			],
			maxZoom: VEHICLE_ZOOM
		});
	});
});
