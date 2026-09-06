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

	/** When the device was last heard from, whatever it had to say. */
	lastSeenAt?: Date | string | null;

	/**
	 * When the position below was recorded, which is not always when the device
	 * was last heard from.
	 *
	 * Event rows carry no coordinates and neither does a sample whose fix had
	 * gone stale, so a device can go on reporting while the newest position
	 * anyone has stays where it last had one.
	 */
	positionAt?: Date | string | null;

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
