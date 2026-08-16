import { expect, test, type Page } from '@playwright/test';

import { resetDatabase } from './fixtures/database';
import { createInitialAdministrator } from './fixtures/users';

function getVehicleSelector(page: Page) {
	return page.getByRole('button', {
		name: 'Select vehicle',
		exact: true
	});
}

async function openAddVehicleDialog(page: Page) {
	const vehicleSelector = getVehicleSelector(page);

	await vehicleSelector.click();

	await page
		.getByRole('button', {
			name: 'Add vehicle',
			exact: true
		})
		.click();

	const dialog = page.getByRole('dialog');

	await expect(dialog).toBeVisible();

	return dialog;
}

test.describe('vehicle selection and creation', () => {
	test.beforeEach(async ({ page }) => {
		await resetDatabase();
		await createInitialAdministrator(page);

		await expect(page).toHaveURL('/');
		await expect(getVehicleSelector(page)).toBeVisible();

		await expect(page.getByTestId('vehicle-map')).toBeVisible();
	});

	test('shows the initially selected vehicle', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);

		await expect(vehicleSelector).toContainText('Škoda Octavia');

		await expect(
			page.getByText('Online', {
				exact: true
			})
		).toBeVisible();
	});

	test('selects another existing vehicle', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);

		await vehicleSelector.click();

		await expect(
			page.getByText('Vehicles', {
				exact: true
			})
		).toBeVisible();

		await page
			.getByRole('button', {
				name: /Volkswagen Golf/
			})
			.click();

		await expect(vehicleSelector).toContainText('Volkswagen Golf');

		await expect(
			page.getByText('Stale', {
				exact: true
			})
		).toBeVisible();
	});

	test('adds a vehicle and selects it', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Development Vehicle');

		await dialog
			.getByLabel('Device ID', {
				exact: true
			})
			.fill('device-e2e-001');

		await dialog.getByLabel(/Notes/).fill('Created by the Playwright E2E suite');

		await dialog
			.getByRole('button', {
				name: 'Add vehicle',
				exact: true
			})
			.click();

		await expect(dialog).not.toBeVisible();

		// The newly created vehicle becomes the selected vehicle.
		await expect(vehicleSelector).toContainText('Development Vehicle');

		await vehicleSelector.click();

		// The vehicle also appears in the list and is marked offline.
		await expect(
			page.getByRole('button', {
				name: /Development Vehicle Offline/
			})
		).toBeVisible();
	});

	test('does not add a vehicle when the dialog is cancelled', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Cancelled Vehicle');

		await dialog
			.getByLabel('Device ID', {
				exact: true
			})
			.fill('cancelled-device');

		await dialog
			.getByRole('button', {
				name: 'Cancel',
				exact: true
			})
			.click();

		await expect(dialog).not.toBeVisible();
		await expect(vehicleSelector).toContainText('Škoda Octavia');

		await vehicleSelector.click();

		await expect(
			page.getByText('Cancelled Vehicle', {
				exact: true
			})
		).not.toBeVisible();
	});

	test('resets the vehicle form after cancellation', async ({ page }) => {
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Temporary Vehicle');

		await dialog
			.getByLabel('Device ID', {
				exact: true
			})
			.fill('temporary-device');

		await dialog.getByLabel(/Notes/).fill('Temporary notes');

		await dialog
			.getByRole('button', {
				name: 'Cancel',
				exact: true
			})
			.click();

		await expect(dialog).not.toBeVisible();

		const reopenedDialog = await openAddVehicleDialog(page);

		await expect(
			reopenedDialog.getByLabel('Vehicle name', {
				exact: true
			})
		).toHaveValue('');

		await expect(
			reopenedDialog.getByLabel('Device ID', {
				exact: true
			})
		).toHaveValue('');

		await expect(reopenedDialog.getByLabel(/Notes/)).toHaveValue('');

		await reopenedDialog
			.getByRole('button', {
				name: 'Cancel',
				exact: true
			})
			.click();

		await expect(reopenedDialog).not.toBeVisible();
	});
});
