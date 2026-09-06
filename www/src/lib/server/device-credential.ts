import { createHash, randomBytes, randomUUID } from 'node:crypto';

import type { DeviceCredential } from '$lib/vehicles/device-credential';

/**
 * How much randomness a token carries.
 *
 * Thirty-two bytes is the ordinary size for an opaque bearer token and leaves
 * no useful margin for guessing. It is not stretched with a password hash
 * because it has none of a password's weakness: Argon2 and bcrypt are slow to
 * make short, human-chosen secrets expensive to attack, and that slowness
 * would instead be paid by `ingest` on every upload.
 */
const TOKEN_BYTES = 32;

/**
 * Mints an identity and a token for a device.
 *
 * The server is the authority for both. The phone used to invent its own
 * identity and have it retyped here, which meant a typo registered a vehicle
 * that silently never received telemetry.
 */
export function generateDeviceCredential(): DeviceCredential {
	return {
		deviceId: randomUUID(),
		token: generateDeviceToken()
	};
}

/**
 * A fresh token for a device that already has an identity.
 *
 * Unpadded base64url, which is the shape RFC 6750 expects of a bearer token
 * and, more importantly here, part of what gets hashed - `ingest` digests the
 * string exactly as it arrives on the wire, so a change of encoding is a
 * change of credential.
 */
export function generateDeviceToken(): string {
	return randomBytes(TOKEN_BYTES).toString('base64url');
}

/**
 * The form `known_devices.token_hash` stores.
 *
 * Only the hash is kept, so a copy of the database yields nothing that can be
 * replayed - and nobody, including this application, can show a token again
 * once it has been handed over. Losing one is answered by rotating it rather
 * than by looking it up.
 */
export function hashDeviceToken(token: string): string {
	return createHash('sha256').update(token, 'utf8').digest('hex');
}
