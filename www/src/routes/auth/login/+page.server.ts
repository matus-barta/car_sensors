import { fail, redirect } from '@sveltejs/kit';
import { APIError } from 'better-auth/api';

import { auth } from '$lib/server/auth';
import { isApplicationSetupRequired } from '$lib/server/application-setup';

import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	if (await isApplicationSetupRequired()) {
		redirect(303, '/auth/setup');
	}

	if (locals.user) {
		redirect(303, '/');
	}

	return {};
};

export const actions: Actions = {
	default: async (event) => {
		const formData = await event.request.formData();

		const email = formData.get('email')?.toString().trim().toLowerCase() ?? '';
		const password = formData.get('password')?.toString() ?? '';

		if (!email || !password) {
			return fail(400, {
				message: 'Enter your email address and password.',
				email
			});
		}

		try {
			await auth.api.signInEmail({
				headers: event.request.headers,
				body: {
					email,
					password
				}
			});
		} catch (error) {
			if (error instanceof APIError) {
				return fail(400, {
					message: 'The email address or password is incorrect.',
					email
				});
			}

			console.error('Sign-in failed:', error);

			return fail(500, {
				message: 'Sign-in is temporarily unavailable.',
				email
			});
		}

		redirect(303, '/');
	}
};
