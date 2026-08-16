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
	import { setVehicleState, VehicleState } from '$lib/state/vehicles.svelte';

	let { children, data } = $props();

	const vehicleState = setVehicleState(
		new VehicleState(
			[
				{
					id: 'car-1',
					name: 'Škoda Octavia',
					status: 'online',
					lastSeenAt: new Date(),
					latitude: 48.1486,
					longitude: 17.1077
				},
				{
					id: 'car-2',
					name: 'Volkswagen Golf',
					status: 'stale',
					lastSeenAt: new Date(Date.now() - 12 * 60 * 1000),
					latitude: 48.156,
					longitude: 17.115
				},
				{
					id: 'car-3',
					name: 'Toyota Corolla',
					status: 'offline',
					lastSeenAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
					latitude: 48.141,
					longitude: 17.095
				}
			],
			'car-1'
		)
	);

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
