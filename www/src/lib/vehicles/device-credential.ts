/**
 * What a phone needs to upload, and how it is handed over.
 *
 * The identity and the token do different jobs and are deliberately not the
 * same value: `deviceId` names the vehicle and is not a secret, while `token`
 * proves the request came from the phone that vehicle belongs to. Rotating the
 * token therefore withdraws a credential without disturbing the identity every
 * stored sample is filed under.
 */
export interface DeviceCredential {
	deviceId: string;
	token: string;
}

/**
 * The payload encoded into the pairing QR code.
 *
 * Versioned because the phone reading it ships separately from the server
 * writing it, so a future change needs something to branch on rather than a
 * guess at the shape.
 */
export interface DevicePairingPayload extends DeviceCredential {
	v: 1;
}

export const DEVICE_PAIRING_PAYLOAD_VERSION = 1;

export function toPairingPayload(credential: DeviceCredential): string {
	const payload: DevicePairingPayload = {
		v: DEVICE_PAIRING_PAYLOAD_VERSION,
		deviceId: credential.deviceId,
		token: credential.token
	};

	return JSON.stringify(payload);
}
