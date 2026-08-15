<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';
	import type { VehicleStatus } from '$lib/models/vehicle';
	import {
		getVehicleStatusDotClass,
		getVehicleStatusLabel
	} from '$lib/presentation/vehicle-status';

	interface Props {
		status: VehicleStatus;
		class?: string;
		pulse?: boolean;
	}

	let { status, class: className, pulse = true }: Props = $props();
</script>

<Badge
	variant="outline"
	class={[
		'min-w-20 justify-center border-border/70 bg-background/85 text-foreground backdrop-blur-sm',
		className
	]}
>
	<span class="relative mr-1.5 flex size-2 shrink-0" aria-hidden="true">
		{#if status === 'online' && pulse}
			<span
				class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-60 motion-reduce:animate-none"
			></span>
		{/if}

		<span class={['relative inline-flex size-2 rounded-full', getVehicleStatusDotClass(status)]}
		></span>
	</span>

	{getVehicleStatusLabel(status)}
</Badge>
