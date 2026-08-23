<script lang="ts">
	import type { Snippet } from 'svelte';

	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';

	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import { setVehicleState, VehicleState } from '$lib/vehicles/vehicle-state.svelte';
	import { createVehicle, getVehicles } from '$lib/vehicles/vehicle.remote';

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
	const vehicleState = setVehicleState(new VehicleState(vehicles));

	let addVehicleDialogOpen = $state(false);

	async function addVehicle(input: AddVehicleInput): Promise<void> {
		const createdVehicle = await createVehicle(input);

		/*
		 * `createVehicle(input).updates(vehicles)` would save this round-trip,
		 * but under the current experimental remote-function runtime it resolves
		 * without ever applying the refreshed list, so the new vehicle would not
		 * appear. Refresh explicitly until that settles.
		 */
		await vehicles.refresh();

		vehicleState.selectVehicle(createdVehicle.id);
	}

	function openAddVehicleDialog(): void {
		addVehicleDialogOpen = true;
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
		onSignOut={signOut}
	/>

	<AddVehicleDialog bind:open={addVehicleDialogOpen} onSubmit={addVehicle} />

	<main class="relative min-h-0 flex-1">
		{@render children()}
	</main>
</div>
