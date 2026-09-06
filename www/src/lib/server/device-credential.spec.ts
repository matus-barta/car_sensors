import { describe, expect, it } from 'vitest';

import {
	generateDeviceCredential,
	generateDeviceToken,
	hashDeviceToken
} from './device-credential';

/**
 * The same token and digest `ingest`'s integration tests use, and the digest
 * came from `shasum -a 256` rather than from either implementation.
 *
 * This is the whole contract between the two services: `www` stores the hash
 * and `ingest` recomputes it from what a device presents. If they disagree
 * about the algorithm or the encoding, nothing ever authenticates - and each
 * would still pass its own tests if those only checked it agreed with itself.
 */
const KNOWN_TOKEN = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8';
const KNOWN_SHA256 = 'ea866a757e4c38babfa8127cbe9a409d3e1f93a00ff1488ff735fcf917afffd0';

describe('hashDeviceToken', () => {
	it('produces the digest ingest will recompute', () => {
		expect(hashDeviceToken(KNOWN_TOKEN)).toBe(KNOWN_SHA256);
	});

	it('renders the digest as lowercase hex, which is what the column holds', () => {
		expect(hashDeviceToken(generateDeviceToken())).toMatch(/^[0-9a-f]{64}$/);
	});
});

describe('generateDeviceToken', () => {
	it('renders 32 bytes as unpadded base64url', () => {
		/*
		 * The encoding is part of what gets hashed, because `ingest` digests the
		 * string exactly as it arrives on the wire. Padding or a different
		 * alphabet would be a different credential.
		 */
		expect(generateDeviceToken()).toMatch(/^[A-Za-z0-9_-]{43}$/);
	});

	it('does not repeat itself', () => {
		const tokens = new Set(Array.from({ length: 100 }, () => generateDeviceToken()));

		expect(tokens.size).toBe(100);
	});
});

describe('generateDeviceCredential', () => {
	it('issues an identity the server chose rather than one a phone invented', () => {
		expect(generateDeviceCredential().deviceId).toMatch(
			/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
		);
	});

	it('pairs each identity with its own token', () => {
		const first = generateDeviceCredential();
		const second = generateDeviceCredential();

		expect(first.token).not.toBe(second.token);
		expect(first.deviceId).not.toBe(second.deviceId);
	});
});
