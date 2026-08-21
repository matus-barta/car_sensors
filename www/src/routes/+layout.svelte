<script lang="ts">
	import './layout.css';

	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';

	import favicon from '$lib/assets/favicon.svg';

	import AddVehicleDialog, {
		type AddVehicleInput
	} from '$lib/components/add-vehicle-dialog.svelte';

	import AppHeader, { type HeaderUser } from '$lib/components/app-header.svelte';
	import { setVehicleState, VehicleState } from '$lib/vehicles/vehicle-state.svelte';
	import { createVehicle, getVehicles } from '$lib/vehicles/vehicle.remote';

	let { children, data } = $props();

	const vehicleState = setVehicleState(new VehicleState());

	let vehiclesQuery: ReturnType<typeof getVehicles> | null = null;

	let vehicleInitializationStarted = false;
	let addVehicleDialogOpen = $state(false);

	const headerUser = $derived<HeaderUser | null>(
		data.user
			? {
					name: data.user.name,
					email: data.user.email,
					image: data.user.image
				}
			: null
	);

	async function initializeVehicles(): Promise<void> {
		if (!vehiclesQuery) {
			return;
		}

		vehicleState.setLoading(true);
		vehicleState.setError(null);

		try {
			const vehicles = await vehiclesQuery;

			vehicleState.replaceVehicles(vehicles);
		} catch (cause) {
			console.error('Failed to load vehicles:', cause);

			vehicleState.setError(
				cause instanceof Error ? cause.message : 'The vehicle list could not be loaded.'
			);
		} finally {
			vehicleState.setLoading(false);
		}
	}

	$effect(() => {
		if (!data.user || vehicleInitializationStarted) {
			return;
		}

		vehicleInitializationStarted = true;
		vehiclesQuery = getVehicles();

		void initializeVehicles();
	});

	async function refreshVehicles(): Promise<void> {
		if (!vehiclesQuery) {
			throw new Error('The vehicle list is not initialized.');
		}

		await vehiclesQuery.refresh();

		const refreshedVehicles = vehiclesQuery.current;

		if (!refreshedVehicles) {
			throw new Error('The vehicle list could not be refreshed.');
		}

		vehicleState.replaceVehicles(refreshedVehicles);
		vehicleState.setError(null);
	}

	async function addVehicle(input: AddVehicleInput): Promise<void> {
		const createdVehicle = await createVehicle({
			name: input.name,
			deviceId: input.deviceId,
			notes: input.notes
		});

		await refreshVehicles();

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

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>Car Sensors</title>
</svelte:head>

{#if headerUser}
	<div class="flex h-dvh flex-col overflow-hidden">
		<AppHeader
			user={headerUser}
			vehicles={vehicleState.vehicles}
			vehiclesLoading={vehicleState.loading}
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
