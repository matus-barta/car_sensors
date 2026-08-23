import { tick } from 'svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { pollWhileVisible } from './poll.svelte';

/** Replaces `document.hidden`, which is otherwise read-only. */
function setDocumentHidden(hidden: boolean): void {
	Object.defineProperty(document, 'hidden', {
		configurable: true,
		get: () => hidden
	});

	document.dispatchEvent(new Event('visibilitychange'));
}

describe('pollWhileVisible', () => {
	afterEach(() => {
		vi.useRealTimers();

		Reflect.deleteProperty(document, 'hidden');
	});

	it('runs the action on the interval', async () => {
		vi.useFakeTimers();

		const action = vi.fn();

		const destroy = $effect.root(() => {
			pollWhileVisible(action, 1000);
		});

		await tick();

		// Nothing is polled up front: the caller has just loaded the data.
		expect(action).not.toHaveBeenCalled();

		/*
		 * The async variant drains microtasks between the timer callbacks, so
		 * each run settles before the next tick — advancing synchronously would
		 * fire all three callbacks while the first is still in flight and the
		 * overlap guard would drop two of them.
		 */
		await vi.advanceTimersByTimeAsync(3000);

		destroy();

		expect(action).toHaveBeenCalledTimes(3);
	});

	it('pauses while hidden and catches up on becoming visible', async () => {
		vi.useFakeTimers();

		const action = vi.fn();

		const destroy = $effect.root(() => {
			pollWhileVisible(action, 1000);
		});

		await tick();

		setDocumentHidden(true);

		await vi.advanceTimersByTimeAsync(5000);

		expect(action).not.toHaveBeenCalled();

		setDocumentHidden(false);
		await tick();

		destroy();

		expect(action).toHaveBeenCalledTimes(1);
	});

	it('does not stack a slow action up behind the next tick', async () => {
		vi.useFakeTimers();

		let release: (() => void) | undefined;
		const action = vi.fn(
			() =>
				new Promise<void>((resolve) => {
					release = resolve;
				})
		);

		const destroy = $effect.root(() => {
			pollWhileVisible(action, 1000);
		});

		await tick();

		await vi.advanceTimersByTimeAsync(3000);

		// Still the first run: the action has not resolved yet.
		expect(action).toHaveBeenCalledTimes(1);

		release?.();
		await tick();

		await vi.advanceTimersByTimeAsync(1000);

		destroy();

		expect(action).toHaveBeenCalledTimes(2);
	});

	it('keeps polling after the action fails', async () => {
		vi.useFakeTimers();

		const action = vi.fn(() => Promise.reject(new Error('offline')));

		const destroy = $effect.root(() => {
			pollWhileVisible(action, 1000);
		});

		await tick();

		await vi.advanceTimersByTimeAsync(2000);

		destroy();

		expect(action).toHaveBeenCalledTimes(2);
	});

	it('stops polling once the effect is destroyed', async () => {
		vi.useFakeTimers();

		const action = vi.fn();

		const destroy = $effect.root(() => {
			pollWhileVisible(action, 1000);
		});

		await tick();
		destroy();

		await vi.advanceTimersByTimeAsync(5000);

		expect(action).not.toHaveBeenCalled();
	});
});
