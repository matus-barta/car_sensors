<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import LogOut from '@lucide/svelte/icons/log-out';
	import Plus from '@lucide/svelte/icons/plus';
	import UserRound from '@lucide/svelte/icons/user-round';
	import Check from '@lucide/svelte/icons/check';

	import { Button } from '$lib/components/ui/button';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu';
	import * as Popover from '$lib/components/ui/popover';
	import { Separator } from '$lib/components/ui/separator';
	import type { VehicleSummary } from '$lib/models/vehicle';
	import { formatRelativeTime } from '$lib/utils/date';
	import {
		getVehicleStatusDotClass,
		getVehicleStatusLabel
	} from '$lib/presentation/vehicle-status';
	import UserAvatar from '$lib/components/user-avatar.svelte';
	import { resolve } from '$app/paths';
	import VehicleStatusBadge from '$lib/components/vehicle-status-badge.svelte';

	export interface HeaderUser {
		name: string;
		email: string;
		image?: string | null;
	}

	interface Props {
		user: HeaderUser;
		vehicles?: VehicleSummary[];
		selectedVehicleId?: string | null;
		onVehicleSelect?: (vehicleId: string) => void;
		onAddVehicle?: () => void;
		onSignOut?: () => void | Promise<void>;
	}

	let {
		user,
		vehicles = [],
		selectedVehicleId = null,
		onVehicleSelect,
		onAddVehicle,
		onSignOut
	}: Props = $props();

	let vehicleMenuOpen = $state(false);
	let signingOut = $state(false);

	const selectedVehicle = $derived(
		vehicles.find((vehicle) => vehicle.id === selectedVehicleId) ?? null
	);

	function selectVehicle(vehicleId: string) {
		onVehicleSelect?.(vehicleId);
		vehicleMenuOpen = false;
	}

	function addVehicle() {
		vehicleMenuOpen = false;
		onAddVehicle?.();
	}

	async function signOut() {
		if (!onSignOut || signingOut) return;

		signingOut = true;

		try {
			await onSignOut();
		} finally {
			signingOut = false;
		}
	}
</script>

<header
	class="relative z-50 flex h-16 shrink-0 items-center border-b border-border/70 bg-background/95 px-3 backdrop-blur supports-backdrop-filter:bg-background/85 md:px-4"
>
	<div class="flex min-w-0 flex-1 items-center gap-3">
		<a
			href={resolve('/')}
			class="flex shrink-0 items-center gap-2 rounded-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
		>
			<span
				class="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground"
			>
				<CarFront class="size-5" aria-hidden="true" />
			</span>

			<span class="hidden text-sm font-semibold sm:inline"> Car Sensors </span>
		</a>

		<Separator orientation="vertical" class="hidden h-6 sm:block" />

		<Popover.Root bind:open={vehicleMenuOpen}>
			<Popover.Trigger>
				{#snippet child({ props })}
					<Button
						{...props}
						variant="outline"
						aria-label="Select vehicle"
						class="h-10 max-w-[18rem] min-w-0 justify-between gap-3 px-3"
					>
						<span class="flex min-w-0 items-center gap-2">
							<CarFront class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />

							<span class="min-w-0 truncate">
								{selectedVehicle?.name ?? 'Select vehicle'}
							</span>

							{#if selectedVehicle}
								<span
									class={[
										'size-2 shrink-0 rounded-full',
										getVehicleStatusDotClass(selectedVehicle.status)
									]}
									aria-hidden="true"
								></span>
							{/if}
						</span>

						<ChevronsUpDown class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
					</Button>
				{/snippet}
			</Popover.Trigger>

			<Popover.Content align="start" class="w-[min(22rem,calc(100vw-1.5rem))] p-1">
				<div class="px-2 py-1.5">
					<p class="text-sm font-medium">Vehicles</p>
					<p class="text-xs text-muted-foreground">Select a vehicle to display on the map.</p>
				</div>

				<Separator class="my-1" />

				<div class="max-h-72 overflow-y-auto p-1">
					{#if vehicles.length > 0}
						{#each vehicles as vehicle (vehicle.id)}
							{@const lastSeen = formatRelativeTime(vehicle.lastSeenAt)}
							<Button
								variant="ghost"
								class={[
									'h-auto w-full justify-start gap-3 px-2 py-2 text-left',
									vehicle.id === selectedVehicleId && 'bg-accent'
								]}
								aria-current={vehicle.id === selectedVehicleId ? 'true' : undefined}
								onclick={() => selectVehicle(vehicle.id)}
							>
								<span
									class="relative flex size-9 shrink-0 items-center justify-center rounded-md border"
								>
									<CarFront class="size-4 text-muted-foreground" aria-hidden="true" />

									<span
										class={[
											'absolute -right-0.5 -bottom-0.5 size-2.5 rounded-full ring-2 ring-background',
											getVehicleStatusDotClass(vehicle.status)
										]}
										aria-hidden="true"
									></span>
								</span>

								<span class="min-w-0 flex-1">
									<span class="block truncate text-sm font-medium">
										{vehicle.name}
									</span>

									<span class="block truncate text-xs text-muted-foreground">
										{getVehicleStatusLabel(vehicle.status)}

										{#if lastSeen}
											· {lastSeen}
										{/if}
									</span>
								</span>

								{#if vehicle.id === selectedVehicleId}
									<Check class="size-4 shrink-0 text-primary" aria-label="Selected vehicle" />
								{/if}
							</Button>
						{/each}
					{:else}
						<div class="px-3 py-6 text-center">
							<CarFront class="mx-auto mb-2 size-6 text-muted-foreground" aria-hidden="true" />
							<p class="text-sm font-medium">No vehicles</p>
							<p class="mt-1 text-xs text-muted-foreground">
								Add your first vehicle to start tracking.
							</p>
						</div>
					{/if}
				</div>

				<Separator class="my-1" />

				<Button variant="ghost" class="w-full justify-start" onclick={addVehicle}>
					<Plus class="size-4" aria-hidden="true" />
					Add vehicle
				</Button>
			</Popover.Content>
		</Popover.Root>

		{#if selectedVehicle}
			{@const vehicle = selectedVehicle}

			<VehicleStatusBadge status={vehicle.status} class="hidden md:inline-flex" />
		{/if}
	</div>

	<DropdownMenu.Root>
		<DropdownMenu.Trigger>
			{#snippet child({ props })}
				<Button
					{...props}
					variant="ghost"
					class="h-11 min-w-0 gap-2 rounded-full px-1.5 sm:rounded-lg sm:pr-3"
					aria-label="Open user menu"
				>
					<UserAvatar name={user.name} image={user.image} class="size-8" />

					<span class="hidden min-w-0 text-left lg:block">
						<span class="block max-w-40 truncate text-sm font-medium">{user.name}</span>
						<span class="block max-w-40 truncate text-xs text-muted-foreground">
							{user.email}
						</span>
					</span>
				</Button>
			{/snippet}
		</DropdownMenu.Trigger>

		<DropdownMenu.Content align="end" class="w-64">
			<DropdownMenu.Label class="font-normal">
				<div class="flex items-center gap-3">
					<UserAvatar name={user.name} image={user.image} class="size-9" />

					<div class="min-w-0">
						<p class="truncate text-sm font-medium">{user.name}</p>
						<p class="truncate text-xs text-muted-foreground">{user.email}</p>
					</div>
				</div>
			</DropdownMenu.Label>

			<DropdownMenu.Separator />

			<DropdownMenu.Item onclick={addVehicle}>
				<Plus class="size-4" aria-hidden="true" />
				Add vehicle
			</DropdownMenu.Item>

			<DropdownMenu.Item disabled>
				<UserRound class="size-4" aria-hidden="true" />
				Account
			</DropdownMenu.Item>

			<DropdownMenu.Separator />

			<DropdownMenu.Item variant="destructive" disabled={signingOut} onclick={signOut}>
				<LogOut class="size-4" aria-hidden="true" />
				{signingOut ? 'Signing out…' : 'Sign out'}
			</DropdownMenu.Item>
		</DropdownMenu.Content>
	</DropdownMenu.Root>
</header>
