<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import LogOut from '@lucide/svelte/icons/log-out';
	import Plus from '@lucide/svelte/icons/plus';
	import Check from '@lucide/svelte/icons/check';
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';

	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu';
	import * as Empty from '$lib/components/ui/empty';
	import * as Popover from '$lib/components/ui/popover';
	import { Separator } from '$lib/components/ui/separator';
	import type { VehicleWithStatus } from '$lib/vehicles/vehicle';
	import { clock } from '$lib/utils/clock.svelte';
	import { formatRelativeTime } from '$lib/utils/date';
	import { getErrorMessage } from '$lib/utils/error';
	import {
		getVehicleStatusDotClass,
		getVehicleStatusLabel
	} from '$lib/vehicles/vehicle-status-style';
	import UserAvatar from '$lib/components/user-avatar.svelte';
	import { resolve } from '$app/paths';
	import VehicleStatusBadge from '$lib/components/vehicle-status-badge.svelte';
	import VehicleSelectorSkeleton from '$lib/components/vehicle-selector-skeleton.svelte';
	import ModeSwitcher from './mode-switcher.svelte';

	export interface HeaderUser {
		name: string;
		email: string;
		image?: string | null;
	}

	interface Props {
		user: HeaderUser;
		vehicles?: VehicleWithStatus[];
		vehiclesLoading?: boolean;
		vehiclesError?: string | null;
		selectedVehicleId?: string | null;
		onVehicleSelect?: (vehicleId: string) => void;
		onAddVehicle?: () => void;
		onSignOut?: () => void | Promise<void>;
	}

	let {
		user,
		vehicles = [],
		vehiclesLoading = false,
		vehiclesError = null,
		selectedVehicleId = null,
		onVehicleSelect,
		onAddVehicle,
		onSignOut
	}: Props = $props();

	let vehicleMenuOpen = $state(false);
	let signingOut = $state(false);
	let signOutError = $state<string | null>(null);

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
		signOutError = null;

		try {
			await onSignOut();
		} catch (error) {
			console.error('Failed to sign out:', error);

			signOutError = getErrorMessage(error, 'Signing out failed. Please try again.');
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

		{#if vehiclesLoading}
			<VehicleSelectorSkeleton />
		{:else}
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
								<CarFront class="text-muted-foreground" aria-hidden="true" />

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

							<ChevronsUpDown class="text-muted-foreground" aria-hidden="true" />
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
						{#if vehiclesError}
							<Alert.Root variant="destructive" data-testid="vehicle-list-error">
								<TriangleAlert aria-hidden="true" />
								<Alert.Title>The vehicle list could not be loaded.</Alert.Title>
								<Alert.Description>{vehiclesError}</Alert.Description>
							</Alert.Root>
						{:else if vehicles.length > 0}
							{#each vehicles as vehicle (vehicle.id)}
								{@const lastSeen = formatRelativeTime(vehicle.lastSeenAt, clock.now)}
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
										<CarFront class="text-muted-foreground" aria-hidden="true" />

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
										<Check class="text-primary" aria-label="Selected vehicle" />
									{/if}
								</Button>
							{/each}
						{:else}
							<Empty.Root>
								<Empty.Header>
									<Empty.Media variant="icon">
										<CarFront aria-hidden="true" />
									</Empty.Media>

									<Empty.Title>No vehicles</Empty.Title>

									<Empty.Description>Add your first vehicle to start tracking.</Empty.Description>
								</Empty.Header>
							</Empty.Root>
						{/if}
					</div>

					<Separator class="my-1" />

					<Button variant="ghost" class="w-full justify-start" onclick={addVehicle}>
						<Plus aria-hidden="true" />
						Add vehicle
					</Button>
				</Popover.Content>
			</Popover.Root>
		{/if}
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
				<Plus aria-hidden="true" />
				Add vehicle
			</DropdownMenu.Item>

			<DropdownMenu.Separator />

			<DropdownMenu.Item
				variant="destructive"
				disabled={signingOut}
				closeOnSelect={false}
				onclick={signOut}
			>
				<LogOut aria-hidden="true" />
				{signingOut ? 'Signing out…' : 'Sign out'}
			</DropdownMenu.Item>

			{#if signOutError}
				<Alert.Root variant="destructive" data-testid="sign-out-error">
					<Alert.Description>{signOutError}</Alert.Description>
				</Alert.Root>
			{/if}
		</DropdownMenu.Content>
	</DropdownMenu.Root>

	<ModeSwitcher />
</header>
