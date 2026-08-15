<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import LoaderCircle from '@lucide/svelte/icons/loader-circle';
	import Plus from '@lucide/svelte/icons/plus';

	import { Button } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { Textarea } from '$lib/components/ui/textarea';

	export interface AddVehicleInput {
		name: string;
		deviceId: string;
		notes: string | null;
	}

	interface Props {
		open?: boolean;
		onSubmit?: (vehicle: AddVehicleInput) => void | Promise<void>;
	}

	let { open = $bindable(false), onSubmit }: Props = $props();

	let name = $state('');
	let deviceId = $state('');
	let notes = $state('');
	let submitting = $state(false);
	let errorMessage = $state<string | null>(null);

	const normalizedName = $derived(name.trim());
	const normalizedDeviceId = $derived(deviceId.trim());

	const canSubmit = $derived(
		normalizedName.length > 0 && normalizedDeviceId.length > 0 && !submitting
	);

	function resetForm() {
		name = '';
		deviceId = '';
		notes = '';
		errorMessage = null;
		submitting = false;
	}

	function handleOpenChange(nextOpen: boolean) {
		open = nextOpen;

		if (!nextOpen && !submitting) {
			resetForm();
		}
	}

	function closeDialog() {
		if (submitting) return;

		open = false;
		resetForm();
	}

	async function submit(event: SubmitEvent) {
		event.preventDefault();

		if (submitting) return;

		errorMessage = null;

		if (!normalizedName) {
			errorMessage = 'Enter a vehicle name.';
			return;
		}

		if (!normalizedDeviceId) {
			errorMessage = 'Enter the device ID associated with the vehicle.';
			return;
		}

		submitting = true;

		try {
			await onSubmit?.({
				name: normalizedName,
				deviceId: normalizedDeviceId,
				notes: notes.trim() || null
			});

			open = false;
			resetForm();
		} catch (error) {
			console.error('Failed to add vehicle:', error);

			errorMessage =
				error instanceof Error
					? error.message
					: 'The vehicle could not be added. Please try again.';
		} finally {
			submitting = false;
		}
	}
</script>

<Dialog.Root {open} onOpenChange={handleOpenChange}>
	<Dialog.Content class="sm:max-w-md">
		<form onsubmit={submit}>
			<Dialog.Header>
				<div class="flex items-start gap-3">
					<span
						class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground"
					>
						<CarFront class="size-5" aria-hidden="true" />
					</span>

					<div class="space-y-1">
						<Dialog.Title>Add vehicle</Dialog.Title>

						<Dialog.Description>Associate a vehicle with a telemetry device.</Dialog.Description>
					</div>
				</div>
			</Dialog.Header>

			<div class="grid gap-5 py-5">
				<div class="grid gap-2">
					<Label for="add-vehicle-name">Vehicle name</Label>

					<Input
						id="add-vehicle-name"
						name="name"
						bind:value={name}
						placeholder="Škoda Octavia"
						autocomplete="off"
						disabled={submitting}
						required
					/>

					<p class="text-xs text-muted-foreground">
						A recognizable name used throughout the application.
					</p>
				</div>

				<div class="grid gap-2">
					<Label for="add-vehicle-device-id">Device ID</Label>

					<Input
						id="add-vehicle-device-id"
						name="deviceId"
						bind:value={deviceId}
						placeholder="Enter the device identifier"
						autocomplete="off"
						autocapitalize="none"
						spellcheck="false"
						disabled={submitting}
						required
					/>

					<p class="text-xs text-muted-foreground">The identifier sent by the telemetry device.</p>
				</div>

				<div class="grid gap-2">
					<Label for="add-vehicle-notes">
						Notes
						<span class="font-normal text-muted-foreground">optional</span>
					</Label>

					<Textarea
						id="add-vehicle-notes"
						name="notes"
						bind:value={notes}
						placeholder="Additional information about the vehicle"
						rows={3}
						disabled={submitting}
					/>
				</div>

				{#if errorMessage}
					<p
						class="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
						role="alert"
					>
						{errorMessage}
					</p>
				{/if}
			</div>

			<Dialog.Footer>
				<Button type="button" variant="outline" disabled={submitting} onclick={closeDialog}>
					Cancel
				</Button>

				<Button type="submit" disabled={!canSubmit}>
					{#if submitting}
						<LoaderCircle class="size-4 animate-spin" aria-hidden="true" />
						Adding…
					{:else}
						<Plus class="size-4" aria-hidden="true" />
						Add vehicle
					{/if}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
