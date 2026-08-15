<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';
	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import type { VehicleSummary } from '$lib/models/vehicle';

	const user: HeaderUser = {
		name: 'Matus Barta',
		email: 'matus@example.com'
	};

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

	function selectVehicle(vehicleId: string) {
		selectedVehicleId = vehicleId;
	}

	function openAddVehicleDialog() {
		addVehicleDialogOpen = true;
	}

	async function signOut() {
		// Replace with your Better Auth sign-out action.
		console.log('Sign out');
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

<svelte:head><link rel="icon" href={favicon} /></svelte:head>

<div class="flex h-dvh flex-col overflow-hidden">
	<AppHeader
		{user}
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
