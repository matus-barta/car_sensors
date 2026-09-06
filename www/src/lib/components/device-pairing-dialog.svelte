<script lang="ts">
	import Copy from '@lucide/svelte/icons/copy';
	import Check from '@lucide/svelte/icons/check';
	import QrCode from '@lucide/svelte/icons/qr-code';
	import TriangleAlert from '@lucide/svelte/icons/triangle-alert';

	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';
	import { type DeviceCredential, toPairingPayload } from '$lib/vehicles/device-credential';

	interface Props {
		open?: boolean;
		vehicleName?: string | null;
		credential?: DeviceCredential | null;
		/**
		 * False when the replaced credential could not be cleared from the
		 * ingest cache, so the previous handset may keep uploading briefly.
		 */
		previousCredentialCleared?: boolean;
	}

	let {
		open = $bindable(false),
		vehicleName = null,
		credential = null,
		previousCredentialCleared = true
	}: Props = $props();

	const payload = $derived(credential ? toPairingPayload(credential) : null);

	/*
	 * The library only speaks hex, so the code is drawn in its defaults and
	 * those are swapped for the page's own colours. The two values below are
	 * therefore what to match on rather than a choice of colour: the result
	 * inherits `currentColor`, so the QR is legible in either theme without
	 * carrying a palette of its own.
	 */
	const QR_DEFAULT_FOREGROUND = '#000000ff';
	const QR_DEFAULT_BACKGROUND = '#ffffffff';

	function toThemedQr(svg: string): string {
		return svg
			.replaceAll(QR_DEFAULT_FOREGROUND, 'currentColor')
			.replaceAll(QR_DEFAULT_BACKGROUND, 'none');
	}

	let qrSvg = $state<string | null>(null);
	let qrError = $state<string | null>(null);
	let copied = $state<string | null>(null);

	/*
	 * Rendered in the browser from the credential already in hand. Asking the
	 * server for an image would put the token through another response, and
	 * through whatever caches sit in front of it, for no gain.
	 */
	$effect(() => {
		const value = payload;

		if (!value) {
			qrSvg = null;
			return;
		}

		let cancelled = false;

		void (async () => {
			try {
				const { toString: renderQr } = await import('qrcode');

				const svg = await renderQr(value, {
					type: 'svg',
					errorCorrectionLevel: 'M',
					margin: 1
				});

				if (!cancelled) {
					qrSvg = toThemedQr(svg);
					qrError = null;
				}
			} catch (cause) {
				console.error('Could not render the pairing code:', cause);

				if (!cancelled) {
					qrSvg = null;
					qrError = 'The pairing code could not be drawn. Copy the values instead.';
				}
			}
		})();

		return () => {
			cancelled = true;
		};
	});

	async function copy(label: string, value: string): Promise<void> {
		try {
			await navigator.clipboard.writeText(value);

			copied = label;

			setTimeout(() => {
				if (copied === label) copied = null;
			}, 2000);
		} catch (cause) {
			console.error('Could not copy to the clipboard:', cause);
		}
	}

	function handleOpenChange(nextOpen: boolean): void {
		open = nextOpen;

		if (!nextOpen) {
			copied = null;
		}
	}
</script>

<Dialog.Root {open} onOpenChange={handleOpenChange}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<div class="flex items-start gap-3">
				<span
					class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground"
				>
					<QrCode class="size-5" aria-hidden="true" />
				</span>

				<div class="flex flex-col gap-1">
					<Dialog.Title>Pair a phone</Dialog.Title>

					<Dialog.Description>
						{#if vehicleName}
							Scan this with the Car Sensors app to pair a phone with {vehicleName}.
						{:else}
							Scan this with the Car Sensors app to pair a phone.
						{/if}
					</Dialog.Description>
				</div>
			</div>
		</Dialog.Header>

		<div class="grid gap-4 py-4">
			<Alert.Root variant="destructive">
				<TriangleAlert aria-hidden="true" />
				<Alert.Description>
					This is shown once. Only a hash is stored, so it cannot be looked up again — if it is
					lost, pair the phone again to issue a new one.
				</Alert.Description>
			</Alert.Root>

			{#if !previousCredentialCleared}
				<Alert.Root>
					<Alert.Description>
						The previous phone may keep uploading for a few minutes. The server caches credentials,
						and this one could not be cleared.
					</Alert.Description>
				</Alert.Root>
			{/if}

			{#if qrSvg}
				<div
					class="mx-auto rounded-lg border bg-background p-3 text-foreground [&_svg]:size-48"
					data-testid="device-pairing-qr"
					role="img"
					aria-label="Pairing code"
				>
					<!-- eslint-disable-next-line svelte/no-at-html-tags -->
					{@html qrSvg}
				</div>
			{:else if qrError}
				<Alert.Root variant="destructive">
					<Alert.Description>{qrError}</Alert.Description>
				</Alert.Root>
			{/if}

			{#if credential}
				<p class="text-xs text-muted-foreground">
					If the camera will not cooperate, open this page on the phone and copy the two values
					across.
				</p>

				{#each [{ label: 'Device ID', value: credential.deviceId }, { label: 'Token', value: credential.token }] as field (field.label)}
					<div class="grid gap-1.5">
						<span class="text-xs font-medium">{field.label}</span>

						<div class="flex items-center gap-2">
							<code
								class="min-w-0 flex-1 truncate rounded-md border bg-muted px-2 py-1.5 font-mono text-xs"
								data-testid="device-pairing-{field.label === 'Token' ? 'token' : 'device-id'}"
							>
								{field.value}
							</code>

							<Button
								type="button"
								variant="outline"
								size="icon-sm"
								aria-label="Copy {field.label.toLowerCase()}"
								onclick={() => copy(field.label, field.value)}
							>
								{#if copied === field.label}
									<Check aria-hidden="true" />
								{:else}
									<Copy aria-hidden="true" />
								{/if}
							</Button>
						</div>
					</div>
				{/each}
			{/if}
		</div>

		<Dialog.Footer>
			<Button type="button" onclick={() => handleOpenChange(false)}>Done</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
