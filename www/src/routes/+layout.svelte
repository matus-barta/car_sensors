<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';
	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import type { VehicleSummary } from '$lib/vehicles/vehicle.js';
	import { setVehicleState, VehicleState } from '$lib/vehicles/vehicle-state.svelte.js';

	let { children, data } = $props();

	const initialVehicleId = data.vehicles[0]?.id ?? null;
	const vehicleState = setVehicleState(new VehicleState(data.vehicles, initialVehicleId));

	let addVehicleDialogOpen = $state(false);

	const headerUser = $derived<HeaderUser | null>(
		data.user ? { name: data.user.name, email: data.user.email, image: data.user.image } : null
	);

	function openAddVehicleDialog() {
		addVehicleDialogOpen = true;
	}

	async function signOut() {
		const response = await fetch('/auth/sign-out', { method: 'POST' });

		if (!response.ok) {
			throw new Error('Sign-out failed.');
		}

		await invalidateAll();
		await goto(resolve('/auth/login'));
	}

	async function addVehicle(input: AddVehicleInput) {
		const vehicle: VehicleSummary = {
			id: input.deviceId,
			name: input.name,
			status: 'offline',
			lastSeenAt: null
		};

		vehicleState.addVehicle(vehicle);
	}
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>Car Sensors</title>
</svelte:head>

{#if headerUser}
	<div class="flex h-dvh flex-col overflow-hidden">
		<AppHeader
			user={headerUser}
			vehicles={vehicleState.vehicles}
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
{:else}
	{@render children()}
{/if}
