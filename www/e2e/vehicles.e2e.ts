import { expect, test, type Page } from '@playwright/test';

import {
	createTestTelemetry,
	createTestVehicle,
	getKnownDeviceById,
	resetDatabase
} from './fixtures/database';

import { createInitialAdministrator, signIn, signOut } from './fixtures/users';

function getVehicleSelector(page: Page) {
	return page.getByRole('button', {
		name: 'Select vehicle',
		exact: true
	});
}

function getVehicleInfoCard(page: Page) {
	return page.getByTestId('vehicle-info-card');
}

function getVehicleStatusBadge(page: Page) {
	return getVehicleInfoCard(page).getByTestId('vehicle-status-badge');
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

		const now = Date.now();

		await createTestVehicle({
			deviceId: 'car-1',
			name: 'Škoda Octavia',
			lastSeenAt: new Date(now)
		});

		await createTestVehicle({
			deviceId: 'car-2',
			name: 'Volkswagen Golf',
			lastSeenAt: new Date(now - 12 * 60 * 1000)
		});

		await createTestVehicle({
			deviceId: 'car-3',
			name: 'Toyota Corolla',
			lastSeenAt: new Date(now - 2 * 60 * 60 * 1000)
		});

		await createTestTelemetry({
			deviceId: 'car-1',
			id: 1,
			timestamp: now,
			latitude: 48.1486,
			longitude: 17.1077,
			bearing: 60
		});

		await createTestTelemetry({
			deviceId: 'car-2',
			id: 1,
			timestamp: now - 12 * 60 * 1000,
			latitude: 48.156,
			longitude: 17.115,
			bearing: 210
		});

		await createTestTelemetry({
			deviceId: 'car-3',
			id: 1,
			timestamp: now - 2 * 60 * 60 * 1000,
			latitude: 48.141,
			longitude: 17.095,
			bearing: 320
		});

		await createInitialAdministrator(page);

		await expect(page).toHaveURL('/');
		await expect(getVehicleSelector(page)).toContainText('Škoda Octavia');
		await expect(getVehicleInfoCard(page)).toContainText('Škoda Octavia');
	});

	test('shows the initially selected vehicle', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const vehicleInfoCard = getVehicleInfoCard(page);

		await expect(vehicleSelector).toContainText('Škoda Octavia');

		await expect(vehicleInfoCard).toBeVisible();
		await expect(vehicleInfoCard).toContainText('Škoda Octavia');
		await expect(vehicleInfoCard).toContainText('car-1');

		await expect(getVehicleStatusBadge(page)).toHaveText('Online');
	});

	test('selects another existing vehicle', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const vehicleInfoCard = getVehicleInfoCard(page);

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

		await expect(vehicleInfoCard).toContainText('Volkswagen Golf');
		await expect(vehicleInfoCard).toContainText('car-2');

		await expect(getVehicleStatusBadge(page)).toHaveText('Stale');
	});

	test('adds a vehicle, persists it and selects it', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const vehicleInfoCard = getVehicleInfoCard(page);
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

		const createdVehicle = await getKnownDeviceById('device-e2e-001');

		expect(createdVehicle).not.toBeNull();

		expect(createdVehicle).toMatchObject({
			device_id: 'device-e2e-001',
			name: 'Development Vehicle',
			is_active: true,
			notes: 'Created by the Playwright E2E suite'
		});

		await expect(vehicleSelector).toContainText('Development Vehicle');

		await expect(vehicleInfoCard).toContainText('Development Vehicle');
		await expect(vehicleInfoCard).toContainText('device-e2e-001');

		await expect(getVehicleStatusBadge(page)).toHaveText('Offline');

		await page.reload();

		await expect(page).toHaveURL('/');

		await expect(getVehicleSelector(page)).toContainText('Development Vehicle');

		await expect(getVehicleInfoCard(page)).toContainText('Development Vehicle');
		await expect(getVehicleInfoCard(page)).toContainText('device-e2e-001');

		await expect(getVehicleStatusBadge(page)).toHaveText('Offline');
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

		const cancelledVehicle = await getKnownDeviceById('cancelled-device');

		expect(cancelledVehicle).toBeNull();

		await vehicleSelector.click();

		await expect(
			page.getByText('Cancelled Vehicle', {
				exact: true
			})
		).not.toBeVisible();
	});

	test('explains that a device ID is already registered', async ({ page }) => {
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Duplicate Vehicle');

		await dialog
			.getByLabel('Device ID', {
				exact: true
			})
			.fill('car-1');

		await dialog
			.getByRole('button', {
				name: 'Add vehicle',
				exact: true
			})
			.click();

		await expect(dialog.getByRole('alert')).toHaveText(
			'A vehicle with this device ID already exists.'
		);

		await expect(dialog).toBeVisible();

		await dialog
			.getByRole('button', {
				name: 'Cancel',
				exact: true
			})
			.click();
	});

	test('loads a fresh vehicle list after signing out and back in', async ({ page }) => {
		await signOut(page);

		await createTestVehicle({
			deviceId: 'car-4',
			name: 'Added While Signed Out',
			lastSeenAt: new Date()
		});

		await signIn(page);

		const vehicleSelector = getVehicleSelector(page);

		await vehicleSelector.click();

		await expect(
			page.getByRole('button', {
				name: /Added While Signed Out/
			})
		).toBeVisible();
	});

	test('initializes the vehicle map', async ({ page }) => {
		const map = page.getByTestId('vehicle-map');

		await expect(map).toBeVisible();

		await expect(map).toHaveAttribute('data-map-state', 'ready', {
			timeout: 20_000
		});

		await expect(page.getByTestId('vehicle-map-loading')).toHaveAttribute('aria-hidden', 'true');
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
