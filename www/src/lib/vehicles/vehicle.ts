export type VehicleStatus = 'online' | 'stale' | 'offline';

export interface VehicleSummary {
	id: string;
	name: string;
	status: VehicleStatus;
	lastSeenAt?: Date | string | null;
	latitude?: number | null;
	longitude?: number | null;
	bearing?: number | null;
}
