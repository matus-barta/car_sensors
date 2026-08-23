import { fail, redirect } from '@sveltejs/kit';
import { APIError } from 'better-auth/api';

import { AUTH_SETUP_HEADER, AUTH_SETUP_TOKEN } from '$lib/server/auth-bootstrap';
import { auth } from '$lib/server/auth';
import {
	deleteSetupUser,
	finalizeApplicationSetup,
	getApplicationSetupState,
	isApplicationSetupRequired
} from '$lib/server/application-setup';

import type { Actions, PageServerLoad } from './$types';
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from '$lib/config';

export const load: PageServerLoad = async () => {
	if (!(await isApplicationSetupRequired())) {
		redirect(303, '/auth/login');
	}

	return {};
};

export const actions: Actions = {
	default: async (event) => {
		const formData = await event.request.formData();

		const name = formData.get('name')?.toString().trim() ?? '';
		const email = formData.get('email')?.toString().trim().toLowerCase() ?? '';
		const password = formData.get('password')?.toString() ?? '';
		const confirmPassword = formData.get('confirmPassword')?.toString() ?? '';

		if (name.length < 2) {
			return fail(400, {
				message: 'Enter your name.',
				name,
				email
			});
		}

		if (!email || !email.includes('@')) {
			return fail(400, {
				message: 'Enter a valid email address.',
				name,
				email
			});
		}

		if (password.length < MIN_PASSWORD_LENGTH) {
			return fail(400, {
				message: `Use a password containing at least ${MIN_PASSWORD_LENGTH} characters.`,
				name,
				email
			});
		}

		if (password.length > MAX_PASSWORD_LENGTH) {
			return fail(400, {
				message: `The password must not exceed ${MAX_PASSWORD_LENGTH} characters.`,
				name,
				email
			});
		}

		if (password !== confirmPassword) {
			return fail(400, {
				message: 'The passwords do not match.',
				name,
				email
			});
		}

		const setupState = await getApplicationSetupState();

		if (setupState === 'complete') {
			return fail(409, {
				message: 'Application setup has already been completed.',
				name,
				email
			});
		}

		if (setupState === 'incomplete') {
			return fail(409, {
				message:
					'An earlier setup attempt was interrupted. Sign in with that account to finish setup.',
				name,
				email
			});
		}

		/*
		 * Better Auth writes the account on its own connection, so it cannot take
		 * part in the transaction that claims the setup row. The account is
		 * therefore created first and removed again if the claim does not
		 * succeed, which keeps the installation in a state the UI can recover
		 * from.
		 */
		let createdUserId: string | null = null;

		try {
			const headers = new Headers(event.request.headers);
			headers.set(AUTH_SETUP_HEADER, AUTH_SETUP_TOKEN);

			const signUpResult = await auth.api.signUpEmail({
				headers,
				body: {
					name,
					email,
					password
				}
			});

			createdUserId = signUpResult.user.id;

			if (!(await finalizeApplicationSetup(createdUserId))) {
				await deleteSetupUser(createdUserId);

				return fail(409, {
					message: 'Application setup has already been completed.',
					name,
					email
				});
			}
		} catch (error) {
			if (createdUserId) {
				try {
					await deleteSetupUser(createdUserId);
				} catch (cleanupError) {
					console.error('Failed to roll back the interrupted setup account:', cleanupError);
				}
			}

			if (error instanceof APIError) {
				return fail(400, {
					message: error.message || 'The administrator could not be created.',
					name,
					email
				});
			}

			console.error('Initial administrator setup failed:', error);

			return fail(400, {
				message: error instanceof Error ? error.message : 'The administrator could not be created.',
				name,
				email
			});
		}

		redirect(303, '/');
	}
};
