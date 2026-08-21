import { sql } from 'drizzle-orm';

import { db, schema } from '$lib/server/db';
import type { VehicleSummary } from '$lib/vehicles/vehicle';

import { calculateVehicleStatus } from './vehicle-status';

type VehicleSummaryRow = Record<string, unknown> & {
	id: string;
	name: string | null;
	lastSeenAt: Date | string | null;
	latitude: number | null;
	longitude: number | null;
	bearing: number | null;
};

export interface CreateVehicleRecordInput {
	name: string;
	deviceId: string;
	notes: string | null;
}

export async function getVehicleSummaries(): Promise<VehicleSummary[]> {
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
				latest.latitude,
				latest.longitude,
				COALESCE(
					latest.bearing,
					latest.heading_deg
				) AS bearing
			FROM known_devices AS device
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
				ORDER BY sample.timestamp DESC
				LIMIT 1
			) AS latest ON TRUE
			WHERE device.is_active = TRUE
			ORDER BY
				device.name NULLS LAST,
				device.device_id
		`);

	const now = Date.now();

	return rows.map((row) => ({
		id: row.id,
		name: row.name?.trim() || row.id,
		status: calculateVehicleStatus(row.lastSeenAt, now),
		lastSeenAt: row.lastSeenAt,
		latitude: normalizeLatitude(row.latitude),
		longitude: normalizeLongitude(row.longitude),
		bearing: normalizeBearing(row.bearing)
	}));
}

export async function createVehicle(input: CreateVehicleRecordInput): Promise<VehicleSummary> {
	try {
		const [created] = await db
			.insert(schema.knownDevices)
			.values({
				deviceId: input.deviceId,
				name: input.name,
				notes: input.notes,
				isActive: true
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
			id: created.id,
			name: created.name?.trim() || created.id,
			status: 'offline',
			lastSeenAt: created.lastSeenAt,
			latitude: null,
			longitude: null,
			bearing: null
		};
	} catch (cause) {
		if (isUniqueViolation(cause)) {
			throw new Error('A vehicle with this device ID already exists.', {
				cause
			});
		}

		throw cause;
	}
}

function isUniqueViolation(cause: unknown): cause is { code: string } {
	return typeof cause === 'object' && cause !== null && 'code' in cause && cause.code === '23505';
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
