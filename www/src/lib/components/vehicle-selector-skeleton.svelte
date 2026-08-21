<script lang="ts">
	import { Skeleton } from '$lib/components/ui/skeleton';
</script>

<div
	class="flex h-10 w-56 items-center gap-3 rounded-md border px-3"
	aria-busy="true"
	aria-label="Loading vehicles"
	data-testid="vehicle-selector-skeleton"
>
	<Skeleton class="size-4 shrink-0 rounded-sm" />

	<div class="min-w-0 flex-1">
		<Skeleton class="h-4 w-32 rounded-sm" />
	</div>

	<Skeleton class="size-4 shrink-0 rounded-sm" />
</div>
