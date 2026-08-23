const DEFAULT_INTERVAL_MS = 30_000;

/**
 * Runs `action` on an interval for as long as the tab is visible.
 *
 * Must be called during component initialization: it owns an `$effect`, so the
 * timer and the listener are torn down with that component.
 *
 * A hidden tab is not polled — nothing is on screen to update, and browsers
 * throttle background timers anyway — and becoming visible runs `action`
 * immediately instead of waiting out the remaining interval, so a tab left in
 * the background is not showing hours-old data when it comes back.
 *
 * Failures are swallowed. Polling is best-effort, the caller keeps the failure
 * (a remote query stores it on `error`), and a rejection raised from a timer
 * would only surface as an unhandled rejection.
 */
export function pollWhileVisible(
	action: () => unknown,
	intervalMs: number = DEFAULT_INTERVAL_MS
): void {
	$effect(() => {
		let interval: ReturnType<typeof setInterval> | undefined;
		let running = false;

		async function runAction(): Promise<void> {
			// A slow response must not let the following ticks stack up behind it.
			if (running) {
				return;
			}

			running = true;

			try {
				await action();
			} catch {
				// See the note on failures above.
			} finally {
				running = false;
			}
		}

		function start(): void {
			interval ??= setInterval(() => void runAction(), intervalMs);
		}

		function stop(): void {
			clearInterval(interval);

			interval = undefined;
		}

		function handleVisibilityChange(): void {
			if (document.hidden) {
				stop();

				return;
			}

			void runAction();
			start();
		}

		if (!document.hidden) {
			start();
		}

		document.addEventListener('visibilitychange', handleVisibilityChange);

		return () => {
			stop();

			document.removeEventListener('visibilitychange', handleVisibilityChange);
		};
	});
}
