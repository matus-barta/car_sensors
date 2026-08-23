import { tick } from 'svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Clock } from './clock.svelte';

describe('Clock', () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it('re-runs its readers on every tick', async () => {
		vi.useFakeTimers();

		const clock = new Clock(1000);
		const readings: number[] = [];

		const destroy = $effect.root(() => {
			$effect(() => {
				readings.push(clock.now);
			});
		});

		await tick();

		vi.advanceTimersByTime(1000);
		await tick();

		vi.advanceTimersByTime(1000);
		await tick();

		destroy();

		expect(readings).toHaveLength(3);
		expect(readings[1]).toBe((readings[0] ?? 0) + 1000);
		expect(readings[2]).toBe((readings[0] ?? 0) + 2000);
	});

	it('keeps no timer once its last reader is gone', async () => {
		vi.useFakeTimers();

		const clock = new Clock(1000);
		const readings: number[] = [];

		const destroy = $effect.root(() => {
			$effect(() => {
				readings.push(clock.now);
			});
		});

		await tick();
		destroy();
		await tick();

		vi.advanceTimersByTime(10_000);
		await tick();

		expect(readings).toHaveLength(1);
		expect(vi.getTimerCount()).toBe(0);
	});

	it('reads the current time outside of an effect without starting one', () => {
		vi.useFakeTimers();

		const clock = new Clock(1000);

		expect(clock.now).toBe(Date.now());
		expect(vi.getTimerCount()).toBe(0);
	});
});
