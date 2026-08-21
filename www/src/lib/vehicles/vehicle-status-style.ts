import type { VehicleStatus } from '$lib/vehicles/vehicle';

export type VehicleStatusBadgeVariant = 'default' | 'secondary' | 'outline';

export function getVehicleStatusLabel(status: VehicleStatus): string {
	switch (status) {
		case 'online':
			return 'Online';

		case 'stale':
			return 'Stale';

		case 'offline':
			return 'Offline';
	}
}

export function getVehicleStatusDotClass(status: VehicleStatus): string {
	switch (status) {
		case 'online':
			return 'bg-emerald-500';

		case 'stale':
			return 'bg-amber-500';

		case 'offline':
			return 'bg-slate-400 dark:bg-slate-500';
	}
}

export function getVehicleStatusBadgeVariant(status: VehicleStatus): VehicleStatusBadgeVariant {
	switch (status) {
		case 'online':
			return 'default';

		case 'stale':
			return 'secondary';

		case 'offline':
			return 'outline';
	}
}
