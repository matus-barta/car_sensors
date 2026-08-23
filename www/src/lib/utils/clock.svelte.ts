import { createSubscriber } from 'svelte/reactivity';

const DEFAULT_TICK_MS = 15_000;

/**
 * A `Date.now()` that re-runs whatever read it, on an interval.
 *
 * Anything derived from a timestamp — "3m ago", an online badge — goes stale on
 * its own, without a single byte arriving from the server, so something has to
 * re-read the current time for it. `createSubscriber` starts the interval when
 * the first effect reads `now` and clears it once the last one is gone, so a
 * screen that displays no elapsed time keeps no timer running.
 */
export class Clock {
	#subscribe: () => void;

	constructor(tickMs: number = DEFAULT_TICK_MS) {
		this.#subscribe = createSubscriber((update) => {
			const interval = setInterval(update, tickMs);

			return () => clearInterval(interval);
		});
	}

	get now(): number {
		this.#subscribe();

		return Date.now();
	}
}

/** Shared so the whole app ticks on one interval rather than one per reader. */
export const clock = new Clock();
