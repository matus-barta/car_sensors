<script lang="ts">
	import AddVehicleDialog, { type AddVehicleInput } from '../add-vehicle-dialog.svelte';

	interface Props {
		onSubmit?: (vehicle: AddVehicleInput) => void | Promise<void>;
	}

	let { onSubmit }: Props = $props();

	let open = $state(true);
</script>

<button
	type="button"
	data-testid="open-add-vehicle-dialog"
	onclick={() => {
		open = true;
	}}
>
	Open add vehicle dialog
</button>

<AddVehicleDialog bind:open {onSubmit} />
