import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

import { finalizeApplicationSetup, getApplicationSetupState } from '$lib/server/application-setup';

const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/setup']);

export const load: LayoutServerLoad = async ({ locals, url }) => {
	let setupState = await getApplicationSetupState();
	const currentPath = url.pathname;

	/*
	 * An interrupted setup attempt can leave an account behind without marking
	 * setup complete. Signing in with that account proves ownership, so the
	 * installation finishes itself instead of becoming unreachable.
	 */
	if (setupState === 'incomplete' && locals.user) {
		await finalizeApplicationSetup(locals.user.id);

		setupState = await getApplicationSetupState();
	}

	if (setupState === 'incomplete') {
		if (currentPath !== '/auth/login') {
			redirect(303, '/auth/login');
		}

		return {
			user: null
		};
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

	return {
		// Only the fields the header renders. The full Better Auth user carries
		// account state (role, ban details) the browser has no use for.
		user: locals.user
			? {
					id: locals.user.id,
					name: locals.user.name,
					email: locals.user.email,
					image: locals.user.image ?? null
				}
			: null
	};
};
