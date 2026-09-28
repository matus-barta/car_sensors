/*
 * What the vehicle map's camera should do after an update, decided apart from
 * MapLibre so every case can be tested without a browser. The component asks
 * the one thing only the live map knows - where a vehicle is on screen - and
 * carries out the decision.
 */

export interface MaybeLocated {
	id: string;
	latitude?: number | null;
	longitude?: number | null;
}

export type Located<T extends MaybeLocated> = T & { latitude: number; longitude: number };

export type LngLat = [longitude: number, latitude: number];

/*
 * One vehicle is shown close enough to see the streets around it; several are
 * framed together, never closer than that.
 */
export const VEHICLE_ZOOM = 14;

export type FleetView =
	| { kind: 'center'; center: LngLat; zoom: number }
	| { kind: 'bounds'; bounds: [southWest: LngLat, northEast: LngLat]; maxZoom: number };

export type CameraPlan =
	| { kind: 'stay' }
	| { kind: 'follow'; center: LngLat }
	| { kind: 'frame'; fleetKey: string; view: FleetView };

export interface CameraState<T extends MaybeLocated> {
	vehicles: readonly T[];
	selectedVehicleId: string | null;

	// Engaged: not released by the user moving the map by hand.
	following: boolean;

	// The located vehicles the fleet was last framed on, or `null` to frame it regardless.
	framedFleetKey: string | null;

	isNearViewEdge: (vehicle: Located<T>) => boolean;
}

/**
 * Whether a vehicle has a position the map can show. Vehicles without one are
 * not drawn and play no part in where the camera goes.
 */
export function hasCoordinates<T extends MaybeLocated>(vehicle: T): vehicle is Located<T> {
	return (
		typeof vehicle.latitude === 'number' &&
		Number.isFinite(vehicle.latitude) &&
		vehicle.latitude >= -90 &&
		vehicle.latitude <= 90 &&
		typeof vehicle.longitude === 'number' &&
		Number.isFinite(vehicle.longitude) &&
		vehicle.longitude >= -180 &&
		vehicle.longitude <= 180
	);
}

/**
 * Decides where the camera goes after an update.
 *
 * - Released by the user: it stays where they left it.
 * - A vehicle selected: it follows that vehicle, or stays put while the
 *   vehicle has no position.
 * - Nothing selected: it keeps every located vehicle in view, but moves only
 *   when it has to - when the set of located vehicles changes, or one drives
 *   up to the edge of the view. Movement inside the view leaves it alone.
 */
export function planCamera<T extends MaybeLocated>(state: CameraState<T>): CameraPlan {
	if (!state.following) {
		return { kind: 'stay' };
	}

	if (state.selectedVehicleId !== null) {
		const selected = state.vehicles.find((vehicle) => vehicle.id === state.selectedVehicleId);

		return selected && hasCoordinates(selected)
			? { kind: 'follow', center: [selected.longitude, selected.latitude] }
			: { kind: 'stay' };
	}

	const located = state.vehicles.filter(hasCoordinates);

	if (located.length === 0) {
		return { kind: 'stay' };
	}

	const fleetKey = located
		.map((vehicle) => vehicle.id)
		.sort()
		.join('\n');

	if (fleetKey === state.framedFleetKey && !located.some(state.isNearViewEdge)) {
		return { kind: 'stay' };
	}

	return { kind: 'frame', fleetKey, view: frameFleet(located) };
}

/**
 * The view that shows every one of the given vehicles: one on its own at
 * street level, several by the box around them.
 */
export function frameFleet(located: readonly Located<MaybeLocated>[]): FleetView {
	const [first] = located;

	if (located.length === 1 && first) {
		return { kind: 'center', center: [first.longitude, first.latitude], zoom: VEHICLE_ZOOM };
	}

	const longitudes = located.map((vehicle) => vehicle.longitude);
	const latitudes = located.map((vehicle) => vehicle.latitude);

	return {
		kind: 'bounds',
		bounds: [
			[Math.min(...longitudes), Math.min(...latitudes)],
			[Math.max(...longitudes), Math.max(...latitudes)]
		],
		maxZoom: VEHICLE_ZOOM
	};
}
