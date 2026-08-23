import { createContext } from 'svelte';

import type { RemoteQuery } from '@sveltejs/kit';

import { getErrorMessage } from '$lib/utils/error';

import type { VehicleSummary } from './vehicle';

/**
 * Presents a vehicle query to the UI.
 *
 * The query itself stays the single source of truth for the list, its loading
 * flag and its failure. Only the selection is owned here, and it is resolved
 * against the current list on read so a vehicle that disappears cannot leave a
 * dangling selection behind.
 */
export class VehicleState {
	#query: RemoteQuery<VehicleSummary[]>;
	#requestedVehicleId = $state<string | null>(null);

	constructor(query: RemoteQuery<VehicleSummary[]>) {
		this.#query = query;
	}

	get vehicles(): VehicleSummary[] {
		return this.#query.current ?? [];
	}

	get loading(): boolean {
		return this.#query.loading;
	}

	get error(): string | null {
		const error: unknown = this.#query.error;

		if (!error) {
			return null;
		}

		return getErrorMessage(error, 'The vehicle list could not be loaded.');
	}

	get selectedVehicleId(): string | null {
		const requested = this.#requestedVehicleId;

		if (requested !== null && this.vehicles.some((vehicle) => vehicle.id === requested)) {
			return requested;
		}

		return this.vehicles[0]?.id ?? null;
	}

	get selectedVehicle(): VehicleSummary | null {
		const selectedVehicleId = this.selectedVehicleId;

		if (selectedVehicleId === null) {
			return null;
		}

		return this.vehicles.find((vehicle) => vehicle.id === selectedVehicleId) ?? null;
	}

	selectVehicle(vehicleId: string): void {
		this.#requestedVehicleId = vehicleId;
	}

	async refresh(): Promise<void> {
		await this.#query.refresh();
	}
}

/**
 * `createContext` carries the type across, so consumers need no cast and get a
 * thrown error automatically when the state was never provided.
 */
export const [getVehicleState, setVehicleState] = createContext<VehicleState>();
