import { getContext, setContext } from 'svelte';

import type { VehicleSummary } from '$lib/models/vehicle';

const VEHICLES_CONTEXT = Symbol('vehicles');

export class VehicleState {
	vehicles = $state<VehicleSummary[]>([]);
	selectedVehicleId = $state<string | null>(null);

	constructor(vehicles: VehicleSummary[] = [], selectedVehicleId: string | null = null) {
		this.vehicles = vehicles;
		this.selectedVehicleId = selectedVehicleId;
	}

	selectVehicle(vehicleId: string): void {
		this.selectedVehicleId = vehicleId;
	}

	addVehicle(vehicle: VehicleSummary): void {
		const existingIndex = this.vehicles.findIndex(
			(existingVehicle) => existingVehicle.id === vehicle.id
		);

		if (existingIndex >= 0) {
			this.vehicles[existingIndex] = vehicle;
		} else {
			this.vehicles.push(vehicle);
		}

		this.selectedVehicleId = vehicle.id;
	}
}

export function setVehicleState(state: VehicleState): VehicleState {
	return setContext(VEHICLES_CONTEXT, state);
}

export function getVehicleState(): VehicleState {
	return getContext<VehicleState>(VEHICLES_CONTEXT);
}
