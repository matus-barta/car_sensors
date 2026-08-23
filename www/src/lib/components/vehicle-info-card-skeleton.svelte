<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import { Skeleton } from '$lib/components/ui/skeleton';
</script>

<Card.Root
	class="w-[min(24rem,calc(100vw-2rem))] border-border/70 bg-background/95 shadow-lg backdrop-blur-md"
	aria-busy="true"
	aria-label="Loading vehicle information"
	data-testid="vehicle-info-card-skeleton"
>
	<Card.Header class="pb-4">
		<div class="flex items-start gap-3">
			<Skeleton class="size-11 shrink-0 rounded-lg" />

			<div class="flex min-w-0 flex-1 flex-col gap-2">
				<Skeleton class="h-4 w-36" />
				<Skeleton class="h-3 w-24" />
			</div>

			<Skeleton class="h-5 w-16 rounded-full" />
		</div>
	</Card.Header>

	<Card.Content class="grid gap-3">
		<Skeleton class="h-px w-full" />

		<div class="grid gap-3">
			<div class="flex items-center justify-between gap-4">
				<Skeleton class="h-4 w-20" />
				<Skeleton class="h-4 w-24" />
			</div>

			<div class="flex items-center justify-between gap-4">
				<Skeleton class="h-4 w-20" />
				<Skeleton class="h-4 w-36" />
			</div>
		</div>
	</Card.Content>
</Card.Root>
