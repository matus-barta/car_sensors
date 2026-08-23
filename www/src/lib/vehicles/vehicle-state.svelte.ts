import { createContext } from 'svelte';

import type { RemoteQuery } from '@sveltejs/kit';

import { clock } from '$lib/utils/clock.svelte';
import { getErrorMessage } from '$lib/utils/error';

import { calculateVehicleStatus } from './vehicle-status';

import type { VehicleSummary, VehicleWithStatus } from './vehicle';

/**
 * Presents a vehicle query to the UI.
 *
 * The query itself stays the single source of truth for the list, its loading
 * flag and its failure. Only the selection is owned here, and it is resolved
 * against the current list on read so a vehicle that disappears cannot leave a
 * dangling selection behind.
 *
 * Status is attached here rather than by each consumer: it is derived from
 * `lastSeenAt` against the shared clock, so a vehicle that stops reporting
 * fades from online to stale to offline on its own, with no request involved.
 */
export class VehicleState {
	#query: RemoteQuery<VehicleSummary[]>;
	#requestedVehicleId = $state<string | null>(null);

	#vehicles: VehicleWithStatus[] = $derived.by(() => {
		const now = clock.now;

		return (this.#query.current ?? []).map((vehicle) => ({
			...vehicle,
			status: calculateVehicleStatus(vehicle.lastSeenAt, now)
		}));
	});

	constructor(query: RemoteQuery<VehicleSummary[]>) {
		this.#query = query;
	}

	get vehicles(): VehicleWithStatus[] {
		return this.#vehicles;
	}

	/*
	 * The query reports `loading` during a refresh as well, and the background
	 * poll refreshes it regularly — reporting that would replace the rendered
	 * list with skeletons every half minute, so only the first load counts.
	 */
	get loading(): boolean {
		return this.#query.loading && !this.#query.ready;
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

	get selectedVehicle(): VehicleWithStatus | null {
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
