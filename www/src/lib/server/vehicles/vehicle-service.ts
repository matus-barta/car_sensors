import { eq, sql } from 'drizzle-orm';

import { db, schema } from '$lib/server/db';
import { forgetDeviceCredential } from '$lib/server/device-credential-cache';
import {
	generateDeviceCredential,
	generateDeviceToken,
	hashDeviceToken
} from '$lib/server/device-credential';
import type { DeviceCredential } from '$lib/vehicles/device-credential';
import type { VehicleSummary } from '$lib/vehicles/vehicle';

type VehicleSummaryRow = Record<string, unknown> & {
	id: string;
	name: string | null;
	lastSeenAt: Date | string | null;
	positionAt: Date | string | null;
	latitude: number | null;
	longitude: number | null;
	bearing: number | null;
};

export interface CreateVehicleRecordInput {
	name: string;
	notes: string | null;
}

/**
 * A newly issued credential, and whether the old one is really gone.
 *
 * The token appears here and nowhere else. Only its hash is stored, so this is
 * the single moment it can be shown, and it is not written to a log or kept in
 * any server-side state after the response is sent.
 */
export interface IssuedCredential {
	credential: DeviceCredential;

	/**
	 * False when `ingest`'s cached copy of the previous credential could not be
	 * dropped, so the handset being replaced may keep uploading until that
	 * entry lapses. Reported rather than hidden - see
	 * `forgetDeviceCredential`.
	 */
	previousCredentialCleared: boolean;
}

export interface CreatedVehicle {
	vehicle: VehicleSummary;
	issued: IssuedCredential;
}

/** Raised when a vehicle is asked for that no longer exists. */
export class UnknownVehicleError extends Error {
	constructor(options?: ErrorOptions) {
		super('This vehicle no longer exists.', options);

		this.name = 'UnknownVehicleError';
	}
}

export async function getVehicleSummaries(): Promise<VehicleSummary[]> {
	/*
	 * Two lateral joins rather than one, because "when was this device last
	 * heard from" and "where was it last seen" are different questions with
	 * different answers.
	 *
	 * Plenty of rows carry no position at all. Event rows - `logger_armed`,
	 * `service_started` - never do, and a telemetry sample whose fix went stale
	 * deliberately stores none either, so that a gap reads as a gap rather than
	 * as a vehicle that never moved. Taking the newest row of any kind and
	 * reading its coordinates therefore loses the position of every vehicle
	 * whose last act was to park, which is most of them most of the time.
	 *
	 * So the position comes from the newest row that actually has one, however
	 * old that is, and its age is conveyed by `lastSeenAt` and the status
	 * derived from it. `ingest` already draws the same distinction when it
	 * chooses which sample to announce as live.
	 */
	const rows = await db.execute<VehicleSummaryRow>(sql`
			SELECT
				device.device_id AS id,
				device.name,
				COALESCE(
					device.last_seen_at,
					to_timestamp(
						latest.timestamp / 1000.0
					)
				) AS "lastSeenAt",
				to_timestamp(
					located.timestamp / 1000.0
				) AS "positionAt",
				located.latitude,
				located.longitude,
				COALESCE(
					located.bearing,
					located.heading_deg
				) AS bearing
			FROM known_devices AS device
			LEFT JOIN LATERAL (
				SELECT sample.timestamp
				FROM telemetry_samples AS sample
				WHERE
					sample.device_id =
						device.device_id
				ORDER BY sample.timestamp DESC
				LIMIT 1
			) AS latest ON TRUE
			LEFT JOIN LATERAL (
				SELECT
					sample.timestamp,
					sample.latitude,
					sample.longitude,
					sample.bearing,
					sample.heading_deg
				FROM telemetry_samples AS sample
				WHERE
					sample.device_id =
						device.device_id
					AND sample.latitude IS NOT NULL
					AND sample.longitude IS NOT NULL
				ORDER BY sample.timestamp DESC
				LIMIT 1
			) AS located ON TRUE
			WHERE device.is_active = TRUE
			ORDER BY
				device.name NULLS LAST,
				device.device_id
		`);

	return rows.map((row) => ({
		id: row.id,
		name: row.name?.trim() || row.id,
		lastSeenAt: row.lastSeenAt,
		positionAt: row.positionAt,
		latitude: normalizeLatitude(row.latitude),
		longitude: normalizeLongitude(row.longitude),
		bearing: normalizeBearing(row.bearing)
	}));
}

export async function createVehicle(input: CreateVehicleRecordInput): Promise<CreatedVehicle> {
	/*
	 * The identity is generated here rather than read from a form. It is a
	 * fresh UUID, so a collision with an existing row is not a case worth
	 * handling - unlike the typed field this replaced, where a duplicate was
	 * an ordinary mistake.
	 */
	const credential = generateDeviceCredential();

	const [created] = await db
		.insert(schema.knownDevices)
		.values({
			deviceId: credential.deviceId,
			name: input.name,
			notes: input.notes,
			isActive: true,
			tokenHash: hashDeviceToken(credential.token),
			tokenRotatedAt: new Date().toISOString()
		})
		.returning({
			id: schema.knownDevices.deviceId,
			name: schema.knownDevices.name,
			lastSeenAt: schema.knownDevices.lastSeenAt
		});

	if (!created) {
		throw new Error('The vehicle could not be created.');
	}

	return {
		vehicle: {
			id: created.id,
			name: created.name?.trim() || created.id,
			lastSeenAt: created.lastSeenAt,
			positionAt: null,
			latitude: null,
			longitude: null,
			bearing: null
		},
		issued: {
			credential,

			// Nothing has ever authenticated as this identity, so there is no
			// cached credential for it to still be holding.
			previousCredentialCleared: true
		}
	};
}

/**
 * Issues a fresh token for a vehicle that already has an identity.
 *
 * This is what pairing a replacement handset does, and what withdrawing a lost
 * one does - they are the same operation seen from either end. `device_id` is
 * deliberately untouched, so the vehicle keeps every sample ever filed under
 * it while the credential that reaches it changes.
 */
export async function rotateDeviceToken(deviceId: string): Promise<IssuedCredential> {
	const token = generateDeviceToken();

	const [updated] = await db
		.update(schema.knownDevices)
		.set({
			tokenHash: hashDeviceToken(token),
			tokenRotatedAt: new Date().toISOString()
		})
		.where(eq(schema.knownDevices.deviceId, deviceId))
		.returning({
			id: schema.knownDevices.deviceId
		});

	if (!updated) {
		throw new UnknownVehicleError();
	}

	/*
	 * Only after the row is written. Clearing the cache first would leave a
	 * window in which `ingest` re-read and re-cached the credential this is
	 * about to replace.
	 */
	const previousCredentialCleared = await forgetDeviceCredential(deviceId);

	return {
		credential: {
			deviceId,
			token
		},
		previousCredentialCleared
	};
}

function normalizeLatitude(value: number | null): number | null {
	return typeof value === 'number' && Number.isFinite(value) && value >= -90 && value <= 90
		? value
		: null;
}

function normalizeLongitude(value: number | null): number | null {
	return typeof value === 'number' && Number.isFinite(value) && value >= -180 && value <= 180
		? value
		: null;
}

function normalizeBearing(value: number | null): number | null {
	if (typeof value !== 'number' || !Number.isFinite(value)) {
		return null;
	}

	return ((value % 360) + 360) % 360;
}
