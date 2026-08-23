<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import ClockIcon from '@lucide/svelte/icons/clock';
	import MapPin from '@lucide/svelte/icons/map-pin';
	import Navigation from '@lucide/svelte/icons/navigation';

	import * as Card from '$lib/components/ui/card';
	import { Separator } from '$lib/components/ui/separator';
	import type { VehicleWithStatus } from '$lib/vehicles/vehicle';
	import { clock } from '$lib/utils/clock.svelte';
	import { formatRelativeTime } from '$lib/utils/date';
	import VehicleStatusBadge from '$lib/components/vehicle-status-badge.svelte';

	interface Props {
		vehicle: VehicleWithStatus;
	}

	let { vehicle }: Props = $props();

	// Reading the clock is what makes this recount while the card stays open.
	const lastSeen = $derived(formatRelativeTime(vehicle.lastSeenAt, clock.now));

	const coordinates = $derived(
		typeof vehicle.latitude === 'number' &&
			Number.isFinite(vehicle.latitude) &&
			typeof vehicle.longitude === 'number' &&
			Number.isFinite(vehicle.longitude)
			? `${vehicle.latitude.toFixed(5)}, ${vehicle.longitude.toFixed(5)}`
			: null
	);

	const bearing = $derived(
		typeof vehicle.bearing === 'number' && Number.isFinite(vehicle.bearing)
			? `${Math.round(vehicle.bearing)}°`
			: null
	);
</script>

<Card.Root
	class="w-[min(24rem,calc(100vw-2rem))] min-w-0 overflow-hidden border-border/70 bg-background/95 shadow-lg backdrop-blur-md"
	data-testid="vehicle-info-card"
>
	<Card.Header class="min-w-0 pb-4">
		<div class="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-3">
			<div
				class="flex size-11 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground"
			>
				<CarFront class="size-5" aria-hidden="true" />
			</div>

			<div class="min-w-0">
				<div class="flex min-w-0 items-center gap-2">
					<Card.Title class="min-w-0 flex-1 truncate text-base">
						{vehicle.name}
					</Card.Title>

					<VehicleStatusBadge status={vehicle.status} pulse={false} class="shrink-0" />
				</div>

				<Card.Description class="mt-1 font-mono text-xs leading-relaxed break-all">
					{vehicle.id}
				</Card.Description>
			</div>
		</div>
	</Card.Header>

	<Card.Content class="grid gap-3">
		<Separator />

		<div class="grid gap-2 text-sm">
			<div class="flex min-w-0 items-center gap-3">
				<ClockIcon class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />

				<span class="shrink-0 text-muted-foreground"> Last seen </span>

				<span
					class="ml-auto min-w-0 truncate text-right font-medium"
					title={lastSeen ?? 'No telemetry received'}
				>
					{lastSeen ?? 'No telemetry received'}
				</span>
			</div>

			<div class="flex min-w-0 items-center gap-3">
				<MapPin class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />

				<span class="shrink-0 text-muted-foreground"> Location </span>

				<span
					class="ml-auto min-w-0 truncate text-right font-mono text-xs font-medium"
					title={coordinates ?? 'Unavailable'}
				>
					{coordinates ?? 'Unavailable'}
				</span>
			</div>

			{#if bearing}
				<div class="flex min-w-0 items-center gap-3">
					<Navigation class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />

					<span class="text-muted-foreground">Bearing</span>

					<span class="ml-auto font-medium">
						{bearing}
					</span>
				</div>
			{/if}
		</div>
	</Card.Content>
</Card.Root>
