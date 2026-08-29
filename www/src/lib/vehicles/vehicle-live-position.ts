import type { VehicleLivePosition, VehicleSummary } from './vehicle';

/**
 * Overlays a live position onto a vehicle summary.
 *
 * The summary's own `lastSeenAt`/coordinates already come from the database,
 * so `live` only ever improves on them for the one vehicle being streamed;
 * a `null` live position (nothing streamed yet, or live tracking disabled)
 * leaves the summary untouched.
 */
export function mergeLivePosition(
	vehicle: VehicleSummary,
	live: VehicleLivePosition | null
): VehicleSummary {
	if (!live) {
		return vehicle;
	}

	return {
		...vehicle,
		lastSeenAt: live.lastSeenAt,
		latitude: live.latitude,
		longitude: live.longitude,
		bearing: live.bearing
	};
}
