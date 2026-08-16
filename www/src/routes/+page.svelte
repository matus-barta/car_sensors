<script lang="ts">
	import VehicleMap from '$lib/components/vehicle-map.svelte';
	import { getVehicleState } from '$lib/state/vehicles.svelte';

	const vehicleState = getVehicleState();
</script>

<svelte:head>
	<title>Vehicle map | Car Sensors</title>
</svelte:head>

<VehicleMap
	vehicles={vehicleState.vehicles}
	selectedVehicleId={vehicleState.selectedVehicleId}
	onVehicleSelect={(vehicleId) => vehicleState.selectVehicle(vehicleId)}
/>
