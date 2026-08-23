<script lang="ts">
	import VehicleInfoCard from '$lib/components/vehicle-info-card.svelte';
	import VehicleInfoCardSkeleton from '$lib/components/vehicle-info-card-skeleton.svelte';
	import VehicleMap from '$lib/components/vehicle-map.svelte';

	import { getVehicleState } from '$lib/vehicles/vehicle-state.svelte.js';

	const vehicleState = getVehicleState();
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

	{#if vehicleState.loading}
		<div class="pointer-events-none absolute top-4 left-4 z-30 md:top-5 md:left-5">
			<VehicleInfoCardSkeleton />
		</div>
	{:else if vehicleState.selectedVehicle}
		{@const selectedVehicle = vehicleState.selectedVehicle}

		<div class="pointer-events-none absolute top-4 left-4 z-30 md:top-5 md:left-5">
			<div class="pointer-events-auto">
				<VehicleInfoCard vehicle={selectedVehicle} />
			</div>
		</div>
	{/if}
</div>
