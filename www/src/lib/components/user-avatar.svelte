<script lang="ts">
	import * as Avatar from '$lib/components/ui/avatar';
	import { getInitials } from '$lib/utils/user';

	interface Props {
		name: string;
		image?: string | null;
		class?: string;
	}

	let { name, image = null, class: className = 'size-8' }: Props = $props();

	const initials = $derived(getInitials(name));
</script>

<Avatar.Root class={className}>
	<Avatar.Image src={image} alt="user avatar" />
	<Avatar.Fallback>{initials}</Avatar.Fallback>
</Avatar.Root>
