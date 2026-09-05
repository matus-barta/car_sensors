<script lang="ts">
	import VehicleInfoCard from '$lib/components/vehicle-info-card.svelte';
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

	<!--
		No skeleton while the first fetch is in flight. Nothing can know yet
		whether it will end in a selection, so a placeholder card is a promise
		that an empty fleet then breaks by having it vanish. The map carries its
		own loading indicator, so the page is not silent in the meantime.
	-->
	{#if vehicleState.selectedVehicle}
		{@const selectedVehicle = vehicleState.selectedVehicle}

		<div class="pointer-events-none absolute top-4 left-4 z-30 md:top-5 md:left-5">
			<div class="pointer-events-auto">
				<VehicleInfoCard vehicle={selectedVehicle} />
			</div>
		</div>
	{/if}
</div>
