import { json } from '@sveltejs/kit';

import { auth } from '$lib/server/auth';

import type { RequestHandler } from './$types';

export const POST: RequestHandler = async ({ request }) => {
	await auth.api.signOut({
		headers: request.headers
	});

	return json({
		success: true
	});
};
