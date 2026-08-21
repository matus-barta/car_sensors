import { sql } from 'drizzle-orm';

import type { VehicleSummary } from '$lib/vehicles/vehicle';
import { db } from '$lib/server/db';
import { calculateVehicleStatus } from './vehicle-status';

type VehicleSummaryRow = Record<string, unknown> & {
	id: string;
	name: string | null;
	lastSeenAt: Date | string | null;
	latitude: number | null;
	longitude: number | null;
	bearing: number | null;
};

export async function getVehicleSummaries(): Promise<VehicleSummary[]> {
	const result = await db.execute<VehicleSummaryRow>(sql`
		SELECT
			device.device_id AS id,
			device.name,
			COALESCE(
				device.last_seen_at,
				to_timestamp(latest.timestamp / 1000.0)
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
			WHERE sample.device_id = device.device_id
			ORDER BY sample.timestamp DESC
			LIMIT 1
		) AS latest ON TRUE
		WHERE device.is_active = TRUE
		ORDER BY
			device.name NULLS LAST,
			device.device_id
	`);

	const now = Date.now();

	return result.map((row) => ({
		id: row.id,
		name: row.name?.trim() || row.id,
		status: calculateVehicleStatus(row.lastSeenAt, now),
		lastSeenAt: row.lastSeenAt,
		latitude: normalizeLatitude(row.latitude),
		longitude: normalizeLongitude(row.longitude),
		bearing: normalizeBearing(row.bearing)
	}));
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
