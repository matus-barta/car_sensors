import { command, getRequestEvent, query } from '$app/server';

import { error } from '@sveltejs/kit';
import { z } from 'zod';

import { liveTracking, type LiveSample } from '$lib/server/live-tracking';
import {
	createVehicle as createVehicleRecord,
	DuplicateDeviceIdError,
	getVehicleSummaries
} from '$lib/server/vehicles/vehicle-service';

import type { VehicleLivePosition, VehicleSummary } from './vehicle';

const createVehicleSchema = z.object({
	name: z
		.string()
		.trim()
		.min(1, 'Enter a vehicle name.')
		.max(120, 'The vehicle name must not exceed 120 characters.'),

	deviceId: z
		.string()
		.trim()
		.min(1, 'Enter a device ID.')
		.max(255, 'The device ID must not exceed 255 characters.'),

	notes: z
		.string()
		.trim()
		.max(2000, 'The notes must not exceed 2,000 characters.')
		.nullable()
		.transform((value) => value || null)
});

export type CreateVehicleInput = z.infer<typeof createVehicleSchema>;

function requireAuthenticatedUser(): void {
	const event = getRequestEvent();

	if (!event.locals.user) {
		error(401, 'Authentication is required.');
	}
}

export const getVehicles = query(async (): Promise<VehicleSummary[]> => {
	requireAuthenticatedUser();

	return getVehicleSummaries();
});

function toLivePosition(sample: LiveSample | null): VehicleLivePosition | null {
	if (!sample) {
		return null;
	}

	return {
		lastSeenAt: new Date(sample.timestamp).toISOString(),
		latitude: sample.latitude,
		longitude: sample.longitude,
		bearing: sample.bearing ?? null
	};
}

/**
 * Streams the live position of a single device: the stored snapshot on
 * connect, then each announcement Valkey delivers for it.
 *
 * The session is checked once, here, rather than on every value the stream
 * yields - unlike a request-response query, a live connection can outlive
 * the sign-out that would otherwise have ended it. The app shell tears the
 * client down on sign-out, which closes the rest of the way.
 *
 * Yields `null` and ends the stream immediately when live tracking is
 * disabled (`REDIS_URL` unset): there is nothing to add over the periodic
 * poll every vehicle already gets.
 */
export const watchVehicle = query.live(z.string().min(1), async function* (deviceId) {
	requireAuthenticatedUser();

	if (!liveTracking) {
		yield null;

		return;
	}

	yield toLivePosition(await liveTracking.getStoredSample(deviceId));

	const { request } = getRequestEvent();

	for await (const sample of liveTracking.watchSamples(deviceId, request.signal)) {
		yield toLivePosition(sample);
	}
});

export const createVehicle = command(
	createVehicleSchema,
	async (input): Promise<VehicleSummary> => {
		requireAuthenticatedUser();

		try {
			return await createVehicleRecord(input);
		} catch (cause) {
			/*
			 * SvelteKit replaces any thrown error that is not an HttpError with a
			 * generic "Internal Error", so anything the user should read has to be
			 * raised through `error()`.
			 */
			if (cause instanceof DuplicateDeviceIdError) {
				error(409, cause.message);
			}

			console.error('Failed to create vehicle:', cause);

			error(500, 'The vehicle could not be created.');
		}
	}
);
