<script lang="ts">
	import CarFront from '@lucide/svelte/icons/car-front';
	import ShieldCheck from '@lucide/svelte/icons/shield-check';

	import { enhance } from '$app/forms';

	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';

	import type { ActionData } from './$types';
	import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from '$lib/config';

	let { form }: { form: ActionData } = $props();
</script>

<svelte:head>
	<title>Set up Car Sensors</title>
</svelte:head>

<div class="flex min-h-dvh items-center justify-center bg-muted/30 p-4">
	<Card.Root class="w-full max-w-md">
		<Card.Header class="flex flex-col gap-4">
			<div
				class="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground"
			>
				<CarFront class="size-6" aria-hidden="true" />
			</div>

			<div class="flex flex-col gap-1">
				<Card.Title>Set up Car Sensors</Card.Title>
				<Card.Description>
					Create the initial administrator account. Public registration will be disabled after
					setup.
				</Card.Description>
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
					<Label for="setup-name">Name</Label>
					<Input
						id="setup-name"
						name="name"
						value={form?.name ?? ''}
						autocomplete="name"
						required
					/>
				</div>

				<div class="grid gap-2">
					<Label for="setup-email">Email</Label>
					<Input
						id="setup-email"
						name="email"
						type="email"
						value={form?.email ?? ''}
						autocomplete="email"
						required
					/>
				</div>

				<div class="grid gap-2">
					<Label for="setup-password">Password</Label>
					<Input
						id="setup-password"
						name="password"
						type="password"
						autocomplete="new-password"
						minlength={MIN_PASSWORD_LENGTH}
						maxlength={MAX_PASSWORD_LENGTH}
						required
					/>
					<p class="text-xs text-muted-foreground">
						Use at least {MIN_PASSWORD_LENGTH} characters.
					</p>
				</div>

				<div class="grid gap-2">
					<Label for="setup-confirm-password">Confirm password</Label>
					<Input
						id="setup-confirm-password"
						name="confirmPassword"
						type="password"
						autocomplete="new-password"
						minlength={MIN_PASSWORD_LENGTH}
						maxlength={MAX_PASSWORD_LENGTH}
						required
					/>
				</div>

				<Button type="submit" class="w-full">
					<ShieldCheck aria-hidden="true" />
					Create administrator
				</Button>
			</form>
		</Card.Content>
	</Card.Root>
</div>
