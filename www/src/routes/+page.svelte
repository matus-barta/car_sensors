<script lang="ts">
	import VehicleInfoCard from '$lib/components/vehicle-info-card.svelte';
	import VehicleMap from '$lib/components/vehicle-map.svelte';
	import { getVehicleState } from '$lib/state/vehicles.svelte';

	const vehicleState = getVehicleState();

	const selectedVehicle = $derived(
		vehicleState.vehicles.find((vehicle) => vehicle.id === vehicleState.selectedVehicleId) ?? null
	);
</script>

<svelte:head>
	<title>Vehicle map | Car Sensors</title>
</svelte:head>

<div class="relative size-full min-h-0 overflow-hidden">
	<VehicleMap
		vehicles={vehicleState.vehicles}
		selectedVehicleId={vehicleState.selectedVehicleId}
		onVehicleSelect={(vehicleId) => vehicleState.selectVehicle(vehicleId)}
	/>

	{#if selectedVehicle}
		<div class="pointer-events-none absolute top-4 left-4 z-30 md:right-5 md:bottom-5">
			<div class="pointer-events-auto">
				<VehicleInfoCard vehicle={selectedVehicle} />
			</div>
		</div>
	{/if}
</div>
