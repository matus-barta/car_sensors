import { fail, redirect } from '@sveltejs/kit';

import { isApplicationSetupRequired } from '$lib/server/application-setup';
import { signInWithEmail, type SignInResult } from '$lib/server/sign-in';

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

		let result: SignInResult;

		try {
			result = await signInWithEmail(event, email, password);
		} catch (error) {
			console.error('Sign-in failed:', error);

			return fail(500, {
				message: 'Sign-in is temporarily unavailable.',
				email
			});
		}

		if (result.outcome === 'signed-in') {
			redirect(303, '/');
		}

		switch (result.outcome) {
			case 'rejected':
				return fail(400, {
					message: 'The email address or password is incorrect.',
					email
				});

			case 'rate-limited':
				return fail(429, {
					message: `Too many sign-in attempts. Try again in ${result.retryAfterSeconds} seconds.`,
					email
				});

			case 'failed':
				console.error(`Sign-in failed with status ${result.status}`);

				return fail(500, {
					message: 'Sign-in is temporarily unavailable.',
					email
				});
		}
	}
};
