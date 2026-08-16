import { expect, test } from '@playwright/test';

import {
	getApplicationSetup,
	getUserByEmail,
	getUserCount,
	resetDatabase
} from './fixtures/database';

import { createInitialAdministrator, testAdministrator } from './fixtures/users';

test.describe('initial application setup', () => {
	test.beforeEach(async () => {
		await resetDatabase();
	});

	test('redirects a fresh installation to the setup page', async ({ page }) => {
		await page.goto('/');

		await expect(page).toHaveURL(/\/auth\/setup$/);

		await expect(
			page.getByText('Set up Car Sensors', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Name', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Email', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Password', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Confirm password', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByRole('button', {
				name: 'Create administrator',
				exact: true
			})
		).toBeVisible();
	});

	test('creates the initial administrator and completes setup', async ({ page }) => {
		await createInitialAdministrator(page);

		await expect(page).toHaveURL('/');

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).toBeVisible();

		const user = await getUserByEmail(testAdministrator.email);

		expect(user).not.toBeNull();
		expect(user).toMatchObject({
			name: testAdministrator.name,
			email: testAdministrator.email,
			role: 'admin',
			banned: false
		});

		expect(await getUserCount()).toBe(1);

		const setup = await getApplicationSetup();

		expect(setup).toMatchObject({
			id: 'global',
			completed: true
		});

		expect(setup.completedAt).toBeInstanceOf(Date);
	});

	test('prevents setup from being repeated while authenticated', async ({ page }) => {
		await createInitialAdministrator(page);

		await page.goto('/auth/setup');

		await expect(page).toHaveURL('/');

		await expect(
			page.getByRole('button', {
				name: 'Open user menu',
				exact: true
			})
		).toBeVisible();

		expect(await getUserCount()).toBe(1);
	});

	test('redirects an unauthenticated user away from setup after completion', async ({
		browser,
		page
	}) => {
		const setupContext = await browser.newContext();
		const setupPage = await setupContext.newPage();

		try {
			await createInitialAdministrator(setupPage);
		} finally {
			await setupContext.close();
		}

		await page.goto('/auth/setup');

		await expect(page).toHaveURL(/\/auth\/login$/);

		await expect(
			page.getByLabel('Email', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByLabel('Password', {
				exact: true
			})
		).toBeVisible();

		await expect(
			page.getByRole('button', {
				name: 'Sign in',
				exact: true
			})
		).toBeVisible();

		expect(await getUserCount()).toBe(1);
	});
});
