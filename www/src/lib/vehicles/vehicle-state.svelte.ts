import { createContext } from 'svelte';

import type { RemoteLiveQuery, RemoteLiveQueryFunction, RemoteQuery } from '@sveltejs/kit';

import { clock } from '$lib/utils/clock.svelte';
import { getErrorMessage } from '$lib/utils/error';

import { mergeLivePosition } from './vehicle-live-position';
import { calculateVehicleStatus } from './vehicle-status';

import type { VehicleLivePosition, VehicleSummary, VehicleWithStatus } from './vehicle';

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
	#watchVehicle: RemoteLiveQueryFunction<string, VehicleLivePosition | null>;
	#requestedVehicleId = $state<string | null>(null);

	/*
	 * Resolved against the raw query rather than `this.vehicles`: the latter
	 * is itself derived from this selection (to know which vehicle the live
	 * position belongs to), and reading it here would make the two circular.
	 */
	#resolvedVehicleId: string | null = $derived.by(() => {
		const vehicles = this.#query.current ?? [];
		const requested = this.#requestedVehicleId;

		if (requested !== null && vehicles.some((vehicle) => vehicle.id === requested)) {
			return requested;
		}

		return vehicles[0]?.id ?? null;
	});

	/*
	 * Held here — not just read for its `.current` and discarded — because
	 * SvelteKit reference-counts a live query's connection against how long
	 * something keeps its resource object reachable; a call whose result is
	 * never retained would be eligible for cleanup as soon as it's made,
	 * tearing the stream down right after opening it. Storing it as this
	 * derived's value is what keeps it alive for as long as this vehicle
	 * stays selected, and lets the previous vehicle's connection close once
	 * this one replaces it.
	 */
	#liveQuery: RemoteLiveQuery<VehicleLivePosition | null> | null = $derived.by(() => {
		const vehicleId = this.#resolvedVehicleId;

		return vehicleId ? this.#watchVehicle(vehicleId) : null;
	});

	#livePosition: VehicleLivePosition | null = $derived.by(() => this.#liveQuery?.current ?? null);

	#vehicles: VehicleWithStatus[] = $derived.by(() => {
		const now = clock.now;
		const selectedVehicleId = this.#resolvedVehicleId;
		const livePosition = this.#livePosition;

		return (this.#query.current ?? []).map((vehicle) => {
			const merged =
				vehicle.id === selectedVehicleId ? mergeLivePosition(vehicle, livePosition) : vehicle;

			return {
				...merged,
				status: calculateVehicleStatus(merged.lastSeenAt, now)
			};
		});
	});

	constructor(
		query: RemoteQuery<VehicleSummary[]>,
		watchVehicle: RemoteLiveQueryFunction<string, VehicleLivePosition | null>
	) {
		this.#query = query;
		this.#watchVehicle = watchVehicle;
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
		return this.#resolvedVehicleId;
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
