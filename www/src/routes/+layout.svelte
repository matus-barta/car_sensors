<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';
	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import type { VehicleSummary } from '$lib/models/vehicle';

	let { children, data } = $props();

	let vehicles: VehicleSummary[] = $state([
		{
			id: 'car-1',
			name: 'Škoda Octavia',
			status: 'online',
			lastSeenAt: new Date()
		},
		{
			id: 'car-2',
			name: 'Volkswagen Golf',
			status: 'stale',
			lastSeenAt: new Date(Date.now() - 12 * 60 * 1000)
		},
		{
			id: 'car-3',
			name: 'Toyota Corolla',
			status: 'offline',
			lastSeenAt: new Date(Date.now() - 2 * 60 * 60 * 1000)
		}
	]);

	let selectedVehicleId = $state<string | null>('car-1');
	let addVehicleDialogOpen = $state(false);

	const headerUser = $derived<HeaderUser | null>(
		data.user ? { name: data.user.name, email: data.user.email, image: data.user.image } : null
	);

	function selectVehicle(vehicleId: string) {
		selectedVehicleId = vehicleId;
	}

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
		console.info('Add vehicle:', input);

		// Temporary local implementation.
		// Replace this with a server action or API request.
		const vehicle: VehicleSummary = {
			id: input.deviceId,
			name: input.name,
			status: 'offline',
			lastSeenAt: null
		};

		vehicles.push(vehicle);
		selectedVehicleId = vehicle.id;
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
			{vehicles}
			{selectedVehicleId}
			onVehicleSelect={selectVehicle}
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
