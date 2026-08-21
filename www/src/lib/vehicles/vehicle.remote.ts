import { command, getRequestEvent, query } from '$app/server';
import { error } from '@sveltejs/kit';
import { z } from 'zod';

import type { VehicleSummary } from './vehicle';

import {
	createVehicle as createVehicleRecord,
	getVehicleSummaries
} from '$lib/server/vehicles/vehicle-service';

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
			if (
				cause instanceof Error &&
				cause.message === 'A vehicle with this device ID already exists.'
			) {
				throw cause;
			}

			console.error('Failed to create vehicle:', cause);

			throw new Error('The vehicle could not be created.', { cause: cause });
		}
	}
);
