import type { EventEmitter } from 'node:events';

/**
 * Turns one event on `emitter` into an async iterable of its payloads, ending
 * when `signal` aborts.
 *
 * Waiting for the next event is unbounded — whatever feeds `emitter` might
 * never fire again once its source goes quiet — so cancellation has to be
 * observed from inside the generator rather than left to the caller: an
 * async generator suspended inside a promise that never settles cannot be
 * unblocked by calling `.return()` on it from outside, since nothing resumes
 * its execution to notice the request. This generator instead races every
 * wait against `signal` itself, so it always settles one way or the other.
 *
 * Only the latest payload survives between reads — if several events fire
 * before the consumer asks for the next value, the earlier ones are
 * dropped. That fits a live-position stream, where only the newest value is
 * ever worth showing, but would lose data for a use case that needs every
 * event delivered.
 */
export async function* watchEmitterEvent<T>(
	emitter: EventEmitter,
	eventName: string,
	signal: AbortSignal
): AsyncGenerator<T> {
	let pending: { value: T } | undefined;
	let wake: (() => void) | null = null;

	function onEvent(value: T): void {
		pending = { value };
		wake?.();
	}

	function onAbort(): void {
		wake?.();
	}

	// Registered once for the generator's whole lifetime: a fresh listener per
	// iteration would pile up on `signal`, since only the iteration that
	// happens to be interrupted by the abort ever removes its own.
	emitter.on(eventName, onEvent);
	signal.addEventListener('abort', onAbort);

	try {
		while (!signal.aborted) {
			if (pending) {
				const { value } = pending;

				pending = undefined;

				yield value;

				continue;
			}

			await new Promise<void>((resolve) => {
				wake = resolve;
			});
		}
	} finally {
		emitter.off(eventName, onEvent);
		signal.removeEventListener('abort', onAbort);
	}
}
