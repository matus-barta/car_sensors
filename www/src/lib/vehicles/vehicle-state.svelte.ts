import { getContext, setContext } from 'svelte';

import type { VehicleSummary } from './vehicle';

const VEHICLES_CONTEXT = Symbol('vehicles');

export class VehicleState {
	vehicles = $state<VehicleSummary[]>([]);
	selectedVehicleId = $state<string | null>(null);
	loading = $state(false);
	error = $state<string | null>(null);

	setLoading(loading: boolean): void {
		this.loading = loading;
	}

	setError(error: string | null): void {
		this.error = error;
	}

	constructor(vehicles: VehicleSummary[] = [], selectedVehicleId: string | null = null) {
		this.replaceVehicles(vehicles);

		if (selectedVehicleId && this.vehicles.some((vehicle) => vehicle.id === selectedVehicleId)) {
			this.selectedVehicleId = selectedVehicleId;
		}
	}

	selectVehicle(vehicleId: string): void {
		if (!this.vehicles.some((vehicle) => vehicle.id === vehicleId)) {
			return;
		}

		this.selectedVehicleId = vehicleId;
	}

	replaceVehicles(vehicles: VehicleSummary[]): void {
		const previousSelection = this.selectedVehicleId;

		this.vehicles = [...vehicles];

		const selectionStillExists =
			previousSelection !== null &&
			this.vehicles.some((vehicle) => vehicle.id === previousSelection);

		if (selectionStillExists) {
			return;
		}

		this.selectedVehicleId = this.vehicles[0]?.id ?? null;
	}
}

export function setVehicleState(state: VehicleState): VehicleState {
	return setContext(VEHICLES_CONTEXT, state);
}

export function getVehicleState(): VehicleState {
	const state = getContext<VehicleState>(VEHICLES_CONTEXT);

	if (!state) {
		throw new Error('VehicleState is not available in the current component context.');
	}

	return state;
}
