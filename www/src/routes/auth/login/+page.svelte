<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import LogIn from '@lucide/svelte/icons/log-in';

	import { enhance } from '$app/forms';

	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';

	import type { ActionData } from './$types';

	let { form }: { form: ActionData } = $props();
</script>

<svelte:head>
	<title>Sign in | Car Sensors</title>
</svelte:head>

<div class="flex min-h-dvh items-center justify-center bg-muted/30 p-4">
	<Card.Root class="w-full max-w-sm">
		<Card.Header class="flex flex-col gap-4">
			<div
				class="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground"
			>
				<CarFront class="size-6" aria-hidden="true" />
			</div>

			<div class="flex flex-col gap-1">
				<Card.Title>Sign in</Card.Title>
				<Card.Description>Sign in to monitor your vehicles and telemetry.</Card.Description>
			</div>
		</Card.Header>

		<Card.Content>
			<form method="post" use:enhance class="grid gap-5">
				{#if form?.message}
					<Alert.Root variant="destructive">
						<Alert.Description>{form.message}</Alert.Description>
					</Alert.Root>
				{/if}

				<div class="grid gap-2">
					<Label for="login-email">Email</Label>
					<Input
						id="login-email"
						name="email"
						type="email"
						value={form?.email ?? ''}
						autocomplete="email"
						autofocus
						required
					/>
				</div>

				<div class="grid gap-2">
					<Label for="login-password">Password</Label>
					<Input
						id="login-password"
						name="password"
						type="password"
						autocomplete="current-password"
						required
					/>
				</div>

				<Button type="submit" class="w-full">
					<LogIn aria-hidden="true" />
					Sign in
				</Button>
			</form>
		</Card.Content>
	</Card.Root>
</div>
