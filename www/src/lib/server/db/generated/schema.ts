import {
	pgTable,
	index,
	text,
	boolean,
	timestamp,
	uniqueIndex,
	bigserial,
	bigint,
	doublePrecision,
	real,
	integer,
	check
} from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

export const knownDevices = pgTable(
	'known_devices',
	{
		deviceId: text('device_id').primaryKey().notNull(),
		name: text(),
		isActive: boolean('is_active').default(true).notNull(),
		createdAt: timestamp('created_at', { withTimezone: true, mode: 'string' })
			.defaultNow()
			.notNull(),
		lastSeenAt: timestamp('last_seen_at', { withTimezone: true, mode: 'string' }),
		notes: text()
	},
	(table) => [
		index('idx_known_devices_is_active').using(
			'btree',
			table.isActive.asc().nullsLast().op('bool_ops')
		)
	]
);

export const telemetrySamples = pgTable(
	'telemetry_samples',
	{
		dbId: bigserial('db_id', { mode: 'bigint' }).primaryKey().notNull(),
		// You can use { mode: "bigint" } if numbers are exceeding js number limitations
		id: bigint({ mode: 'number' }),
		deviceId: text('device_id'),
		event: text().notNull(),
		// You can use { mode: "bigint" } if numbers are exceeding js number limitations
		timestamp: bigint({ mode: 'number' }).notNull(),
		payload: text(),
		charging: boolean(),
		powerSource: text('power_source'),
		latitude: doublePrecision(),
		longitude: doublePrecision(),
		altitude: doublePrecision(),
		speedMps: real('speed_mps'),
		speedKmh: real('speed_kmh'),
		bearing: real(),
		accuracyM: real('accuracy_m'),
		provider: text(),
		accelX: real('accel_x'),
		accelY: real('accel_y'),
		accelZ: real('accel_z'),
		accelAccuracy: integer('accel_accuracy'),
		accelAccuracyLabel: text('accel_accuracy_label'),
		gyroX: real('gyro_x'),
		gyroY: real('gyro_y'),
		gyroZ: real('gyro_z'),
		gyroAccuracy: integer('gyro_accuracy'),
		gyroAccuracyLabel: text('gyro_accuracy_label'),
		magX: real('mag_x'),
		magY: real('mag_y'),
		magZ: real('mag_z'),
		magnetAccuracy: integer('magnet_accuracy'),
		magnetAccuracyLabel: text('magnet_accuracy_label'),
		headingDeg: real('heading_deg'),
		uploaded: boolean().default(false).notNull(),
		// You can use { mode: "bigint" } if numbers are exceeding js number limitations
		uploadedAt: bigint('uploaded_at', { mode: 'number' }),
		uploadAttemptCount: integer('upload_attempt_count').default(0).notNull(),
		pressureHpa: real('pressure_hpa'),
		pressureAccuracy: integer('pressure_accuracy'),
		pressureAccuracyLabel: text('pressure_accuracy_label')
	},
	(table) => [
		uniqueIndex('idx_telemetry_device_id_unique').using(
			'btree',
			table.deviceId.asc().nullsLast().op('int8_ops'),
			table.id.asc().nullsLast().op('text_ops')
		),
		index('idx_telemetry_device_timestamp').using(
			'btree',
			table.deviceId.asc().nullsLast().op('text_ops'),
			table.timestamp.asc().nullsLast().op('int8_ops')
		),
		index('idx_telemetry_samples_device_id').using(
			'btree',
			table.deviceId.asc().nullsLast().op('text_ops')
		),
		index('idx_telemetry_timestamp').using(
			'btree',
			table.timestamp.asc().nullsLast().op('int8_ops')
		),
		index('idx_telemetry_uploaded').using('btree', table.uploaded.asc().nullsLast().op('bool_ops')),
		index('idx_telemetry_uploaded_timestamp').using(
			'btree',
			table.uploaded.asc().nullsLast().op('int8_ops'),
			table.timestamp.asc().nullsLast().op('int8_ops')
		)
	]
);

export const applicationSetup = pgTable(
	'application_setup',
	{
		id: text().primaryKey().notNull(),
		completed: boolean().default(false).notNull(),
		completedAt: timestamp('completed_at', { withTimezone: true, mode: 'string' })
	},
	(table) => [check('application_setup_singleton', sql`id = 'global'::text`)]
);
