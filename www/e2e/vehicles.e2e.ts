import { expect, test, type Page } from '@playwright/test';

import {
	createTestTelemetry,
	createTestVehicle,
	getKnownDeviceByName,
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

	test('keeps showing the last known location when the newest rows carry none', async ({
		page
	}) => {
		/*
		 * What a parked car looks like: the last thing it did was arm the
		 * logger, and event rows carry no position. A stale fix stores none
		 * either. Reading coordinates off the newest row of any kind therefore
		 * loses the location of almost every vehicle that is not moving.
		 */
		await createTestTelemetry({
			deviceId: 'car-1',
			id: 2,
			timestamp: Date.now(),
			event: 'logger_armed',
			latitude: null,
			longitude: null
		});

		await page.reload();

		const vehicleInfoCard = getVehicleInfoCard(page);

		await expect(vehicleInfoCard).toContainText('Škoda Octavia');

		// The older located sample, not nothing at all.
		await expect(vehicleInfoCard).toContainText('48.14860');
		await expect(vehicleInfoCard).toContainText('17.10770');
	});

	test('says how old a position is when the device has reported since without one', async ({
		page
	}) => {
		const now = Date.now();

		await createTestVehicle({
			deviceId: 'car-parked',
			name: 'Parked Car',
			lastSeenAt: new Date(now)
		});

		// Its only position is two months old...
		await createTestTelemetry({
			deviceId: 'car-parked',
			id: 1,
			timestamp: now - 60 * 24 * 60 * 60 * 1000,
			latitude: 48.2,
			longitude: 17.2
		});

		// ...while it has gone on reporting rows that carry none.
		await createTestTelemetry({
			deviceId: 'car-parked',
			id: 2,
			timestamp: now,
			event: 'logger_armed',
			latitude: null,
			longitude: null
		});

		await page.reload();

		await getVehicleSelector(page).click();

		await page
			.getByRole('button', {
				name: /Parked Car/
			})
			.click();

		const vehicleInfoCard = getVehicleInfoCard(page);

		await expect(vehicleInfoCard).toContainText('48.20000');

		/*
		 * The badge says online, because the device really is reporting. Without
		 * this line the coordinates beside it would read as current.
		 */
		await expect(getVehicleStatusBadge(page)).toHaveText('Online');
		await expect(page.getByTestId('vehicle-position-age')).toContainText('Position from');
	});

	test('does not label the position age when it arrived with the last contact', async ({
		page
	}) => {
		// Škoda Octavia's newest row is its located sample, so there is nothing
		// to disambiguate and the extra line would only be noise.
		await expect(getVehicleInfoCard(page)).toContainText('48.14860');

		await expect(page.getByTestId('vehicle-position-age')).toHaveCount(0);
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

	test('adds a vehicle, issues a pairing code and selects it', async ({ page }) => {
		const vehicleSelector = getVehicleSelector(page);
		const vehicleInfoCard = getVehicleInfoCard(page);
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Development Vehicle');

		// No device identifier is asked for: the server mints one.
		await expect(
			dialog.getByLabel('Device ID', {
				exact: true
			})
		).toHaveCount(0);

		await dialog.getByLabel(/Notes/).fill('Created by the Playwright E2E suite');

		await dialog
			.getByRole('button', {
				name: 'Add vehicle',
				exact: true
			})
			.click();

		await expect.poll(() => getKnownDeviceByName('Development Vehicle')).not.toBeNull();

		const createdVehicle = await getKnownDeviceByName('Development Vehicle');

		expect(createdVehicle).toMatchObject({
			name: 'Development Vehicle',
			is_active: true,
			notes: 'Created by the Playwright E2E suite'
		});

		const deviceId = String(createdVehicle?.device_id);

		// A generated identity, not one anybody typed.
		expect(deviceId).toMatch(
			/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
		);

		// Only the hash is kept, so the token itself is never recoverable.
		expect(String(createdVehicle?.token_hash)).toMatch(/^[0-9a-f]{64}$/);
		expect(createdVehicle?.token_rotated_at).not.toBeNull();

		// Creation hands straight to pairing; a vehicle nothing can upload to
		// is only half made.
		const pairingToken = page.getByTestId('device-pairing-token');

		await expect(pairingToken).toBeVisible();
		await expect(page.getByTestId('device-pairing-device-id')).toHaveText(deviceId);
		await expect(pairingToken).toHaveText(/^[A-Za-z0-9_-]{43}$/);
		await expect(page.getByTestId('device-pairing-qr')).toBeVisible();

		await page
			.getByRole('button', {
				name: 'Done',
				exact: true
			})
			.click();

		await expect(vehicleSelector).toContainText('Development Vehicle');

		await expect(vehicleInfoCard).toContainText('Development Vehicle');
		await expect(vehicleInfoCard).toContainText(deviceId);

		await expect(getVehicleStatusBadge(page)).toHaveText('Offline');

		await page.reload();

		await expect(page).toHaveURL('/');

		await expect(getVehicleSelector(page)).toContainText('Development Vehicle');

		await expect(getVehicleInfoCard(page)).toContainText('Development Vehicle');

		await expect(getVehicleStatusBadge(page)).toHaveText('Offline');
	});

	test('issues a new token when a replacement phone is paired', async ({ page }) => {
		const before = await getKnownDeviceByName('Škoda Octavia');

		await getVehicleSelector(page).click();

		await page.getByTestId('pair-phone').click();

		await expect(page.getByTestId('device-pairing-token')).toBeVisible();

		/*
		 * The identity is deliberately unchanged. That is what lets a vehicle
		 * survive a replaced handset with its whole history: only the
		 * credential is withdrawn.
		 */
		await expect(page.getByTestId('device-pairing-device-id')).toHaveText('car-1');

		await expect
			.poll(async () => (await getKnownDeviceByName('Škoda Octavia'))?.token_hash)
			.not.toBe(before?.token_hash);

		const after = await getKnownDeviceByName('Škoda Octavia');

		expect(after?.device_id).toBe('car-1');
		expect(String(after?.token_hash)).toMatch(/^[0-9a-f]{64}$/);
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
			.getByRole('button', {
				name: 'Cancel',
				exact: true
			})
			.click();

		await expect(dialog).not.toBeVisible();
		await expect(vehicleSelector).toContainText('Škoda Octavia');

		expect(await getKnownDeviceByName('Cancelled Vehicle')).toBeNull();

		await vehicleSelector.click();

		await expect(
			page.getByText('Cancelled Vehicle', {
				exact: true
			})
		).not.toBeVisible();
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

	test('releases the camera when the map is panned and takes it back on request', async ({
		page
	}) => {
		const map = page.getByTestId('vehicle-map');

		await expect(map).toHaveAttribute('data-map-state', 'ready', {
			timeout: 20_000
		});

		const followToggle = page.getByTestId('vehicle-map-follow-toggle');

		await expect(followToggle).toHaveAttribute('aria-pressed', 'true');

		/*
		 * A real drag rather than a synthetic event: what separates a user gesture
		 * from the component's own camera moves is the DOM event MapLibre attaches
		 * to the first, and only an actual pointer sequence produces one.
		 */
		const canvas = map.locator('canvas').first();
		const box = await canvas.boundingBox();

		expect(box).not.toBeNull();

		if (!box) {
			return;
		}

		const startX = box.x + box.width * 0.5;
		const startY = box.y + box.height * 0.72;

		await page.mouse.move(startX, startY);
		await page.mouse.down();
		await page.mouse.move(startX - 160, startY - 120, { steps: 12 });
		await page.mouse.up();

		await expect(followToggle).toHaveAttribute('aria-pressed', 'false');

		// Panning releases the camera only - the vehicle stays selected.
		await expect(getVehicleInfoCard(page)).toContainText('Škoda Octavia');

		await followToggle.click();

		await expect(followToggle).toHaveAttribute('aria-pressed', 'true');
	});

	test('resets the vehicle form after cancellation', async ({ page }) => {
		const dialog = await openAddVehicleDialog(page);

		await dialog
			.getByLabel('Vehicle name', {
				exact: true
			})
			.fill('Temporary Vehicle');

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
