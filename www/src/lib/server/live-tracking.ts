import { EventEmitter } from 'node:events';

import { env } from '$env/dynamic/private';
import { createClient } from 'redis';
import { z } from 'zod';

import { watchEmitterEvent } from './emitter-stream';

/**
 * Channel `ingest` publishes to for live positions, and the key prefix it
 * stores the newest one under. Both are a contract with `ingest`, which is
 * written in another language and cannot share these definitions - change
 * them on both sides together.
 */
const LIVE_SAMPLE_CHANNEL = 'telemetry:live';

function liveSampleKey(deviceId: string): string {
	return `device:live:${deviceId}`;
}

export const liveSampleSchema = z.object({
	deviceId: z.string(),
	timestamp: z.number(),
	latitude: z.number(),
	longitude: z.number(),
	altitude: z.number().nullable().optional(),
	speedKmh: z.number().nullable().optional(),
	bearing: z.number().nullable().optional(),
	accuracyM: z.number().nullable().optional(),
	charging: z.boolean().nullable().optional(),
	powerSource: z.string().nullable().optional()
});

export type LiveSample = z.infer<typeof liveSampleSchema>;

export function parseLiveSample(raw: string): LiveSample | null {
	try {
		return liveSampleSchema.parse(JSON.parse(raw));
	} catch (cause) {
		console.error('Received an unreadable live sample:', cause);

		return null;
	}
}

export interface LiveTracking {
	/** The newest position `ingest` has stored for the device, if any. */
	getStoredSample(deviceId: string): Promise<LiveSample | null>;

	/** Streams every subsequent announcement for `deviceId` until `signal` aborts. */
	watchSamples(deviceId: string, signal: AbortSignal): AsyncGenerator<LiveSample>;
}

/**
 * One Valkey subscriber for the whole process, fanning announcements out in
 * memory to every interested live query.
 *
 * A subscribed connection cannot issue other commands, so `subscriber` is
 * kept dedicated to `SUBSCRIBE` and `client` handles the `GET` for the stored
 * snapshot.
 */
function createLiveTracking(redisUrl: string): LiveTracking {
	const client = createClient({ url: redisUrl });
	const subscriber = client.duplicate();

	// An unhandled `error` event would otherwise crash the process; the
	// client already reconnects on its own once Valkey comes back.
	client.on('error', (error) => console.error('Redis client error:', error));
	subscriber.on('error', (error) => console.error('Redis subscriber error:', error));

	const emitter = new EventEmitter();

	// Any number of live queries can watch the same or different devices.
	emitter.setMaxListeners(0);

	const ready = (async () => {
		await client.connect();
		await subscriber.connect();

		await subscriber.subscribe(LIVE_SAMPLE_CHANNEL, (message) => {
			const sample = parseLiveSample(message);

			if (sample) {
				emitter.emit(sample.deviceId, sample);
			}
		});
	})();

	ready.catch((error) => console.error('Could not start the live tracking subscriber:', error));

	return {
		async getStoredSample(deviceId) {
			await ready;

			const stored = await client.get(liveSampleKey(deviceId));

			return stored ? parseLiveSample(stored) : null;
		},

		watchSamples(deviceId, signal) {
			return watchEmitterEvent<LiveSample>(emitter, deviceId, signal);
		}
	};
}

/**
 * `null` when `REDIS_URL` is not configured. Live tracking is an enhancement
 * over the periodic poll every vehicle already gets, not a dependency of the
 * application, so development, CI and the end-to-end suite run without it.
 */
export const liveTracking: LiveTracking | null = env.REDIS_URL
	? createLiveTracking(env.REDIS_URL)
	: null;
