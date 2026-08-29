import { EventEmitter } from 'node:events';

import { describe, expect, it } from 'vitest';

import { watchEmitterEvent } from './emitter-stream';

describe('watchEmitterEvent', () => {
	it('yields values emitted after the generator starts', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);
		const first = stream.next();

		emitter.emit('sample', 'a');

		expect(await first).toEqual({ value: 'a', done: false });

		controller.abort();
		expect(await stream.next()).toEqual({ value: undefined, done: true });
	});

	it('keeps only the latest value when several arrive before it is read', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);

		// Let the generator reach its suspension point before emitting, so both
		// events land while it is genuinely waiting for the next one.
		const first = stream.next();
		await Promise.resolve();

		emitter.emit('sample', 'a');
		emitter.emit('sample', 'b');

		expect(await first).toEqual({ value: 'b', done: false });

		controller.abort();
	});

	it('ends without yielding when the signal is already aborted', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		controller.abort();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);

		expect(await stream.next()).toEqual({ value: undefined, done: true });
	});

	it('ends the stream once the signal aborts while waiting', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);
		const next = stream.next();

		controller.abort();

		expect(await next).toEqual({ value: undefined, done: true });
	});

	it('removes its listeners once the signal aborts', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);
		const next = stream.next();

		controller.abort();
		await next;

		expect(emitter.listenerCount('sample')).toBe(0);
	});

	it('removes its listeners when the consumer stops early', async () => {
		const emitter = new EventEmitter();
		const controller = new AbortController();

		const stream = watchEmitterEvent<string>(emitter, 'sample', controller.signal);
		const first = stream.next();

		emitter.emit('sample', 'a');
		await first;

		// Mirrors what a `for await...of` loop does internally on `break`: the
		// generator is suspended at the `yield` that produced 'a', so this
		// resumes and unwinds it immediately.
		await stream.return(undefined);

		expect(emitter.listenerCount('sample')).toBe(0);
	});
});
