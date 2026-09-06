import { env } from '$env/dynamic/private';
import { createClient } from 'redis';

/**
 * The key `ingest` caches a device's credential under.
 *
 * A contract with a service written in another language that cannot share this
 * definition, the same way `telemetry:live` is - change it on both sides
 * together.
 */
function deviceCredentialKey(deviceId: string): string {
	return `device_credential:${deviceId}`;
}

/**
 * Connected on first use rather than at startup.
 *
 * Nothing here runs on the request path: a credential is only forgotten when
 * one is rotated, which happens when somebody pairs a phone. Holding a
 * connection open for that would cost more than making one.
 */
let client: ReturnType<typeof createClient> | null = null;

async function connectedClient(redisUrl: string) {
	if (!client) {
		client = createClient({ url: redisUrl });

		// An unhandled `error` event would otherwise take the process down.
		client.on('error', (error) => console.error('Redis credential client error:', error));
	}

	if (!client.isOpen) {
		await client.connect();
	}

	return client;
}

/**
 * Drops `ingest`'s cached copy of a device's credential.
 *
 * Rotation is only immediate in the database. `ingest` caches what it reads
 * for several minutes, so without this the token just replaced would keep
 * being accepted until that entry lapsed - and locking the previous handset
 * out is the point of rotating, not a side effect of it.
 *
 * Returns whether the cache was actually cleared. `false` is not a failure to
 * swallow: it means the old credential may still work for a few minutes, which
 * is something the person pairing a phone deserves to be told rather than left
 * to discover.
 */
export async function forgetDeviceCredential(deviceId: string): Promise<boolean> {
	const redisUrl = env.REDIS_URL;

	if (!redisUrl) {
		console.warn(
			'REDIS_URL is not configured, so the rotated credential could not be ' +
				'cleared from the ingest cache.'
		);

		return false;
	}

	try {
		const redis = await connectedClient(redisUrl);

		await redis.del(deviceCredentialKey(deviceId));

		return true;
	} catch (cause) {
		console.error('Could not clear the cached device credential:', cause);

		return false;
	}
}
