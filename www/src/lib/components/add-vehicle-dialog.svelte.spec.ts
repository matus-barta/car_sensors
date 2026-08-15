import { afterEach, describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';
import { render } from 'vitest-browser-svelte';

import AddVehicleDialogHarness from './test/add-vehicle-dialog-harness.svelte';

interface VehicleInput {
	name: string;
	deviceId: string;
	notes: string | null;
}

let rendered: ReturnType<typeof render> | undefined;

function renderDialog(
	options: {
		onSubmit?: (vehicle: VehicleInput) => void | Promise<void>;
	} = {}
) {
	rendered = render(AddVehicleDialogHarness, {
		onSubmit: options.onSubmit
	});

	return rendered;
}

function getSubmitButton() {
	return page.getByRole('button', {
		name: 'Add vehicle',
		exact: true
	});
}

function getCancelButton() {
	return page.getByRole('button', {
		name: 'Cancel',
		exact: true
	});
}

function getOpenButton() {
	return page.getByTestId('open-add-vehicle-dialog');
}

function getVehicleNameInput() {
	return page.getByRole('textbox', {
		name: 'Vehicle name',
		exact: true
	});
}

function getDeviceIdInput() {
	return page.getByRole('textbox', {
		name: 'Device ID',
		exact: true
	});
}

function getNotesInput() {
	return page.getByRole('textbox', {
		name: /Notes/
	});
}

function getDialogHeading() {
	return page.getByRole('heading', {
		name: 'Add vehicle',
		exact: true
	});
}

async function flushAnimationFrames(): Promise<void> {
	await new Promise<void>((resolve) => {
		requestAnimationFrame(() => {
			requestAnimationFrame(() => {
				resolve();
			});
		});
	});
}

afterEach(async () => {
	await flushAnimationFrames();

	await rendered?.unmount();
	rendered = undefined;

	await flushAnimationFrames();

	vi.restoreAllMocks();
});

describe('AddVehicleDialog', () => {
	it('renders the form when open', async () => {
		renderDialog();

		await expect.element(getDialogHeading()).toBeInTheDocument();
		await expect.element(getVehicleNameInput()).toBeInTheDocument();
		await expect.element(getDeviceIdInput()).toBeInTheDocument();
		await expect.element(getNotesInput()).toBeInTheDocument();
	});

	it('disables submission while required fields are empty', async () => {
		renderDialog();

		await expect.element(getSubmitButton()).toBeDisabled();
	});

	it('enables submission after entering required fields', async () => {
		renderDialog();

		await getVehicleNameInput().fill('Test Vehicle');
		await getDeviceIdInput().fill('device-001');

		await expect.element(getSubmitButton()).toBeEnabled();
	});

	it('normalizes input and submits the vehicle', async () => {
		const onSubmit = vi.fn();

		renderDialog({
			onSubmit
		});

		await getVehicleNameInput().fill('  Test Vehicle  ');
		await getDeviceIdInput().fill('  device-001  ');
		await getNotesInput().fill('  Development vehicle  ');

		await getSubmitButton().click();

		await vi.waitFor(() => {
			expect(onSubmit).toHaveBeenCalledOnce();
		});

		expect(onSubmit).toHaveBeenCalledWith({
			name: 'Test Vehicle',
			deviceId: 'device-001',
			notes: 'Development vehicle'
		});

		await expect.element(getDialogHeading()).not.toBeInTheDocument();

		await flushAnimationFrames();
	});

	it('converts empty notes to null', async () => {
		const onSubmit = vi.fn();

		renderDialog({
			onSubmit
		});

		await getVehicleNameInput().fill('Test Vehicle');
		await getDeviceIdInput().fill('device-001');

		await getSubmitButton().click();

		await vi.waitFor(() => {
			expect(onSubmit).toHaveBeenCalledOnce();
		});

		expect(onSubmit).toHaveBeenCalledWith({
			name: 'Test Vehicle',
			deviceId: 'device-001',
			notes: null
		});

		await flushAnimationFrames();
	});

	it('closes the dialog after successful submission', async () => {
		const onSubmit = vi.fn();

		renderDialog({
			onSubmit
		});

		await getVehicleNameInput().fill('Test Vehicle');
		await getDeviceIdInput().fill('device-001');

		await getSubmitButton().click();

		await vi.waitFor(() => {
			expect(onSubmit).toHaveBeenCalledOnce();
		});

		await expect.element(getDialogHeading()).not.toBeInTheDocument();

		await flushAnimationFrames();
	});

	it('displays an error and keeps the dialog open when submission fails', async () => {
		const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

		const onSubmit = vi.fn().mockRejectedValue(new Error('The device is already registered.'));

		renderDialog({
			onSubmit
		});

		await getVehicleNameInput().fill('Test Vehicle');
		await getDeviceIdInput().fill('device-001');

		await getSubmitButton().click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('The device is already registered.');

		await expect.element(getDialogHeading()).toBeInTheDocument();

		expect(onSubmit).toHaveBeenCalledOnce();
		expect(consoleError).toHaveBeenCalledOnce();

		await getCancelButton().click();

		await flushAnimationFrames();
	});

	it('closes the dialog when Cancel is selected', async () => {
		renderDialog();

		await getCancelButton().click();

		await expect.element(getDialogHeading()).not.toBeInTheDocument();

		await flushAnimationFrames();
	});

	it('resets the form when the dialog is reopened', async () => {
		renderDialog();

		await getVehicleNameInput().fill('Temporary Vehicle');
		await getDeviceIdInput().fill('temporary-device');
		await getNotesInput().fill('Temporary notes');

		await getCancelButton().click();

		await expect.element(getDialogHeading()).not.toBeInTheDocument();

		await flushAnimationFrames();

		await getOpenButton().click();

		await expect.element(getDialogHeading()).toBeInTheDocument();
		await expect.element(getVehicleNameInput()).toHaveValue('');
		await expect.element(getDeviceIdInput()).toHaveValue('');
		await expect.element(getNotesInput()).toHaveValue('');

		await getCancelButton().click();

		await flushAnimationFrames();
	});
});
