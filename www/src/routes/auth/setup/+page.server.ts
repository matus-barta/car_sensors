import { fail, redirect } from '@sveltejs/kit';
import { APIError } from 'better-auth/api';
import { count, eq, sql } from 'drizzle-orm';

import { AUTH_SETUP_HEADER, AUTH_SETUP_TOKEN } from '$lib/server/auth-bootstrap';
import { auth } from '$lib/server/auth';
import { db, schema } from '$lib/server/db';
import { isApplicationSetupRequired } from '$lib/server/application-setup';

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

		try {
			await db.transaction(async (transaction) => {
				const setupRows = await transaction.execute<{
					completed: boolean;
				}>(
					sql`
						SELECT completed
						FROM application_setup
						WHERE id = 'global'
						FOR UPDATE
					`
				);

				const setupState = setupRows[0];

				if (!setupState || setupState.completed) {
					throw new Error('Application setup has already been completed.');
				}

				const [existingUsers] = await transaction
					.select({
						value: count()
					})
					.from(schema.user);

				if (existingUsers.value > 0) {
					throw new Error('Application setup has already been completed.');
				}

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

				const createdUserId = signUpResult.user.id;

				const updatedUsers = await transaction
					.update(schema.user)
					.set({
						role: 'admin'
					})
					.where(eq(schema.user.id, createdUserId))
					.returning({
						id: schema.user.id
					});

				if (updatedUsers.length !== 1) {
					throw new Error('The initial administrator could not be assigned.');
				}

				await transaction
					.update(schema.applicationSetup)
					.set({
						completed: true,
						completedAt: sql`now()`
					})
					.where(eq(schema.applicationSetup.id, 'global'));
			});
		} catch (error) {
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
