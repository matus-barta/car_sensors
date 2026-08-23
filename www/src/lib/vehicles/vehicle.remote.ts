import { command, getRequestEvent, query } from '$app/server';

import { error } from '@sveltejs/kit';
import { z } from 'zod';

import {
	createVehicle as createVehicleRecord,
	DuplicateDeviceIdError,
	getVehicleSummaries
} from '$lib/server/vehicles/vehicle-service';

import type { VehicleSummary } from './vehicle';

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
