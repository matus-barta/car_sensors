<script lang="ts">
	import type { Snippet } from 'svelte';

	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';

	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import DevicePairingDialog from '$lib/components/device-pairing-dialog.svelte';
	import { pollWhileVisible } from '$lib/utils/poll.svelte';
	import type { DeviceCredential } from '$lib/vehicles/device-credential';
	import { setVehicleState, VehicleState } from '$lib/vehicles/vehicle-state.svelte';
	import {
		createVehicle,
		getVehicles,
		pairNewPhone,
		watchVehicle
	} from '$lib/vehicles/vehicle.remote';

	interface Props {
		user: HeaderUser;
		children: Snippet;
	}

	let { user, children }: Props = $props();

	/*
	 * Created here rather than in the root layout so the query only runs for a
	 * signed-in visitor, and so signing out and back in mounts a fresh one
	 * instead of reusing the previous session's list.
	 */
	const vehicles = getVehicles();
	const vehicleState = setVehicleState(new VehicleState(vehicles, watchVehicle));

	/*
	 * Positions and `lastSeenAt` only change when a device uploads, and `ingest`
	 * writes `last_seen_at` at most once every 30 seconds per device, so polling
	 * faster than that would return the same rows. Everything that ages between
	 * two polls — the relative time, the status — is derived from the clock and
	 * needs no request at all.
	 */
	pollWhileVisible(() => vehicleState.refresh());

	let addVehicleDialogOpen = $state(false);

	/*
	 * The token exists in the browser only for as long as this dialog is open.
	 * Nothing persists it, because only its hash was stored and showing it a
	 * second time is precisely what the scheme gives up in exchange for the
	 * database being worthless if copied.
	 */
	let pairingDialogOpen = $state(false);
	let pairingCredential = $state<DeviceCredential | null>(null);
	let pairingVehicleName = $state<string | null>(null);
	let pairingPreviousCleared = $state(true);

	function showPairingCode(
		vehicleName: string | null,
		credential: DeviceCredential,
		previousCredentialCleared: boolean
	): void {
		pairingVehicleName = vehicleName;
		pairingCredential = credential;
		pairingPreviousCleared = previousCredentialCleared;
		pairingDialogOpen = true;
	}

	async function addVehicle(input: AddVehicleInput): Promise<void> {
		const created = await createVehicle(input);

		/*
		 * `createVehicle(input).updates(vehicles)` would save this round-trip,
		 * but under the current experimental remote-function runtime it resolves
		 * without ever applying the refreshed list, so the new vehicle would not
		 * appear. Refresh explicitly until that settles.
		 */
		await vehicles.refresh();

		vehicleState.selectVehicle(created.vehicle.id);

		// Straight on to pairing: a vehicle nothing can upload to is half made.
		showPairingCode(
			created.vehicle.name,
			created.issued.credential,
			created.issued.previousCredentialCleared
		);
	}

	function openAddVehicleDialog(): void {
		addVehicleDialogOpen = true;
	}

	async function pairPhone(vehicleId: string, vehicleName: string | null): Promise<void> {
		const issued = await pairNewPhone(vehicleId);

		showPairingCode(vehicleName, issued.credential, issued.previousCredentialCleared);
	}

	async function signOut(): Promise<void> {
		const response = await fetch('/auth/sign-out', {
			method: 'POST'
		});

		if (!response.ok) {
			throw new Error('Sign-out failed.');
		}

		await invalidateAll();
		await goto(resolve('/auth/login'));
	}
</script>

<div class="flex h-dvh flex-col overflow-hidden">
	<AppHeader
		{user}
		vehicles={vehicleState.vehicles}
		vehiclesLoading={vehicleState.loading}
		vehiclesError={vehicleState.error}
		selectedVehicleId={vehicleState.selectedVehicleId}
		onVehicleSelect={(vehicleId) => vehicleState.selectVehicle(vehicleId)}
		onAddVehicle={openAddVehicleDialog}
		onPairPhone={pairPhone}
		onSignOut={signOut}
	/>

	<AddVehicleDialog bind:open={addVehicleDialogOpen} onSubmit={addVehicle} />

	<DevicePairingDialog
		bind:open={pairingDialogOpen}
		vehicleName={pairingVehicleName}
		credential={pairingCredential}
		previousCredentialCleared={pairingPreviousCleared}
	/>

	<main class="relative min-h-0 flex-1">
		{@render children()}
	</main>
</div>
