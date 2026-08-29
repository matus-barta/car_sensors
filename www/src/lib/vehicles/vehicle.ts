export type VehicleStatus = 'online' | 'stale' | 'offline';

/**
 * A vehicle as the server knows it.
 *
 * There is deliberately no `status` here: status is a reading of `lastSeenAt`
 * taken at a particular moment, and a value computed on the server would keep
 * claiming "online" for as long as the browser holds on to it.
 */
export interface VehicleSummary {
	id: string;
	name: string;
	lastSeenAt?: Date | string | null;
	latitude?: number | null;
	longitude?: number | null;
	bearing?: number | null;
}

/** A vehicle with the status derived for the moment it is being displayed. */
export interface VehicleWithStatus extends VehicleSummary {
	status: VehicleStatus;
}

/**
 * The newest position `watchVehicle` has for a device, streamed from Valkey
 * rather than read from the periodic vehicle poll.
 */
export interface VehicleLivePosition {
	lastSeenAt: string;
	latitude: number;
	longitude: number;
	bearing: number | null;
}
