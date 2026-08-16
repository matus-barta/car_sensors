import { expect, test } from '@playwright/test';

import { getUserCount, resetDatabase } from './fixtures/database';
import { createInitialAdministrator, signIn, signOut, testAdministrator } from './fixtures/users';

test.describe('authentication', () => {
	test.beforeEach(async ({ page }) => {
		await resetDatabase();
		await createInitialAdministrator(page);
		await signOut(page);
	});

	test('signs in with valid credentials and preserves the session after reload', async ({
		page
	}) => {
		await signIn(page);

		await expect(page).toHaveURL('/');

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).toBeVisible();

		await page.reload();

		await expect(page).toHaveURL('/');

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).toBeVisible();

		expect(await getUserCount()).toBe(1);
	});

	test('rejects an incorrect password without revealing whether the account exists', async ({
		page
	}) => {
		await page.goto('/auth/login');

		await page
			.getByLabel('Email', {
				exact: true
			})
			.fill(testAdministrator.email);

		await page
			.getByLabel('Password', {
				exact: true
			})
			.fill('incorrect-password');

		await page
			.getByRole('button', {
				name: 'Sign in',
				exact: true
			})
			.click();

		await expect(page).toHaveURL(/\/auth\/login$/);

		await expect(
			page.getByText('The email address or password is incorrect.', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Email', {
				exact: true
			})
		).toHaveValue(testAdministrator.email);

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).not.toBeVisible();

		expect(await getUserCount()).toBe(1);
	});

	test('uses the same error message for an unknown email address', async ({ page }) => {
		await page.goto('/auth/login');

		await page
			.getByLabel('Email', {
				exact: true
			})
			.fill('unknown@example.test');

		await page
			.getByLabel('Password', {
				exact: true
			})
			.fill('incorrect-password');

		await page
			.getByRole('button', {
				name: 'Sign in',
				exact: true
			})
			.click();

		await expect(page).toHaveURL(/\/auth\/login$/);

		await expect(
			page.getByText('The email address or password is incorrect.', {
				exact: true
			})
		).toBeVisible();

		expect(await getUserCount()).toBe(1);
	});

	test('signs out and prevents access to protected routes', async ({ page }) => {
		await signIn(page);
		await signOut(page);

		await expect(page).toHaveURL(/\/auth\/login$/);

		await page.goto('/');

		await expect(page).toHaveURL(/\/auth\/login$/);

		await expect(
			page.getByLabel('Email', {
				exact: true
			})
		).toBeVisible();

		await page.reload();

		await expect(page).toHaveURL(/\/auth\/login$/);

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).not.toBeVisible();
	});

	test('redirects an authenticated user away from the login page', async ({ page }) => {
		await signIn(page);

		await page.goto('/auth/login');

		await expect(page).toHaveURL('/');

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).toBeVisible();
	});

	test('blocks public account registration', async ({ request }) => {
		const response = await request.post('/api/auth/sign-up/email', {
			data: {
				name: 'Unauthorized User',
				email: 'unauthorized@example.test',
				password: 'unauthorized-password'
			}
		});

		expect(response.status()).toBe(403);

		const responseBody = await response.json();

		expect(responseBody).toMatchObject({
			message: 'Public account registration is disabled.'
		});

		expect(await getUserCount()).toBe(1);
	});
});
