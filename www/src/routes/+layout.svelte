<script lang="ts">
	import './layout.css';

	import favicon from '$lib/assets/favicon.svg';

	import { ModeWatcher } from 'mode-watcher';

	import AppShell from '$lib/components/app-shell.svelte';

	let { children, data } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>Car Sensors</title>
</svelte:head>

<ModeWatcher />

{#if data.user}
	{#key data.user.id}
		<AppShell user={data.user}>
			{@render children()}
		</AppShell>
	{/key}
{:else}
	{@render children()}
{/if}
