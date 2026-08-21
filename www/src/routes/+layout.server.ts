import { error, redirect } from '@sveltejs/kit';

import { getApplicationSetupState } from '$lib/server/application-setup';
import { getVehicleSummaries } from '$lib/server/vehicles/vehicle-service';

import type { LayoutServerLoad } from './$types';

const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/setup']);

export const load: LayoutServerLoad = async ({ locals, url }) => {
	const setupState = await getApplicationSetupState();
	const currentPath = url.pathname;

	if (setupState === 'incomplete') {
		error(503, 'Application setup is incomplete. An account exists, but setup was not finalized.');
	}

	if (setupState === 'required' && currentPath !== '/auth/setup') {
		redirect(303, '/auth/setup');
	}

	if (setupState === 'complete' && currentPath === '/auth/setup') {
		redirect(303, locals.user ? '/' : '/auth/login');
	}

	if (setupState === 'complete' && !locals.user && !PUBLIC_AUTH_PATHS.has(currentPath)) {
		redirect(303, '/auth/login');
	}

	if (locals.user && currentPath === '/auth/login') {
		redirect(303, '/');
	}

	const vehicles = locals.user ? await getVehicleSummaries() : [];

	return {
		user: locals.user ?? null,
		setupRequired: setupState === 'required',
		vehicles
	};
};
