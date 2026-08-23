<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';

	import { page } from '$app/state';
	import { resolve } from '$app/paths';

	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
</script>

<svelte:head>
	<title>{page.status} | Car Sensors</title>
</svelte:head>

<div class="flex min-h-dvh items-center justify-center bg-muted/30 p-4">
	<Card.Root class="w-full max-w-md" data-testid="error-page">
		<Card.Header class="space-y-4">
			<div
				class="flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive"
			>
				<TriangleAlert class="size-6" aria-hidden="true" />
			</div>

			<div class="space-y-1">
				<Card.Title>Something went wrong</Card.Title>

				<Card.Description data-testid="error-message">
					{page.error?.message ?? 'The page could not be displayed.'}
				</Card.Description>
			</div>
		</Card.Header>

		<Card.Content>
			<p class="text-xs text-muted-foreground">Status {page.status}</p>
		</Card.Content>

		<Card.Footer>
			<Button href={resolve('/')} class="w-full">
				<CarFront class="size-4" aria-hidden="true" />
				Back to the vehicle map
			</Button>
		</Card.Footer>
	</Card.Root>
</div>
