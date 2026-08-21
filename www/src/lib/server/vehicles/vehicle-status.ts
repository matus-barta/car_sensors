import type { VehicleStatus } from '$lib/vehicles/vehicle';

const ONLINE_THRESHOLD_MS = 2 * 60 * 1000;
const STALE_THRESHOLD_MS = 15 * 60 * 1000;

export function calculateVehicleStatus(
	lastSeenAt: Date | string | null | undefined,
	now = Date.now()
): VehicleStatus {
	if (!lastSeenAt) {
		return 'offline';
	}

	const lastSeenTime =
		lastSeenAt instanceof Date ? lastSeenAt.getTime() : new Date(lastSeenAt).getTime();

	if (!Number.isFinite(lastSeenTime)) {
		return 'offline';
	}

	const age = Math.max(0, now - lastSeenTime);

	if (age <= ONLINE_THRESHOLD_MS) {
		return 'online';
	}

	if (age <= STALE_THRESHOLD_MS) {
		return 'stale';
	}

	return 'offline';
}
