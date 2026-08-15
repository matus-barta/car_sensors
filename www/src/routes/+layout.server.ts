import { redirect } from '@sveltejs/kit';

import { isApplicationSetupRequired } from '$lib/server/application-setup';

import type { LayoutServerLoad } from './$types';

const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/setup']);

export const load: LayoutServerLoad = async ({ locals, url }) => {
	const setupRequired = await isApplicationSetupRequired();
	const currentPath = url.pathname;

	if (setupRequired && currentPath !== '/auth/setup') {
		redirect(303, '/auth/setup');
	}

	if (!setupRequired && currentPath === '/auth/setup') {
		redirect(303, locals.user ? '/' : '/auth/login');
	}

	if (!setupRequired && !locals.user && !PUBLIC_AUTH_PATHS.has(currentPath)) {
		redirect(303, '/auth/login');
	}

	if (locals.user && currentPath === '/auth/login') {
		redirect(303, '/');
	}

	return {
		user: locals.user ?? null,
		setupRequired
	};
};
