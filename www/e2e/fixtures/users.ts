import { expect, type Page } from '@playwright/test';

export interface TestUserCredentials {
	name: string;
	email: string;
	password: string;
}

export const testAdministrator: TestUserCredentials = {
	name: 'Alex Morgan',
	email: 'admin@example.test',
	password: 'test-password-123'
};

/**
 * Creates the initial administrator through the application setup UI.
 *
 * The database must be reset before calling this helper.
 * After successful setup, the page remains authenticated and is redirected
 * to the application root.
 */
export async function createInitialAdministrator(
	page: Page,
	credentials: TestUserCredentials = testAdministrator
): Promise<TestUserCredentials> {
	await page.goto('/');

	await expect(page).toHaveURL(/\/auth\/setup$/);

	await page
		.getByLabel('Name', {
			exact: true
		})
		.fill(credentials.name);

	await page
		.getByLabel('Email', {
			exact: true
		})
		.fill(credentials.email);

	await page
		.getByLabel('Password', {
			exact: true
		})
		.fill(credentials.password);

	await page
		.getByLabel('Confirm password', {
			exact: true
		})
		.fill(credentials.password);

	await Promise.all([
		page.waitForURL((url) => url.pathname === '/'),
		page
			.getByRole('button', {
				name: 'Create administrator',
				exact: true
			})
			.click()
	]);

	await expect(
		page.getByRole('button', {
			name: 'Open user menu',
			exact: true
		})
	).toBeVisible();

	return credentials;
}

/**
 * Signs out the current browser session through the application UI.
 */
export async function signOut(page: Page): Promise<void> {
	await page
		.getByRole('button', {
			name: 'Open user menu',
			exact: true
		})
		.click();

	await Promise.all([
		page.waitForURL((url) => url.pathname === '/auth/login'),
		page
			.getByRole('menuitem', {
				name: 'Sign out',
				exact: true
			})
			.click()
	]);

	await expect(page).toHaveURL(/\/auth\/login$/);
}

/**
 * Signs in through the real login form.
 */
export async function signIn(
	page: Page,
	credentials: TestUserCredentials = testAdministrator
): Promise<void> {
	await page.goto('/auth/login');

	await page
		.getByLabel('Email', {
			exact: true
		})
		.fill(credentials.email);

	await page
		.getByLabel('Password', {
			exact: true
		})
		.fill(credentials.password);

	await Promise.all([
		page.waitForURL((url) => url.pathname === '/'),
		page
			.getByRole('button', {
				name: 'Sign in',
				exact: true
			})
			.click()
	]);

	await expect(
		page.getByRole('button', {
			name: 'Open user menu',
			exact: true
		})
	).toBeVisible();
}
