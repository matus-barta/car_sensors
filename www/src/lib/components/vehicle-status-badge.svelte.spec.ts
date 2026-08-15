import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';

import VehicleStatusBadge from './vehicle-status-badge.svelte';

describe('VehicleStatusBadge', () => {
	it('renders the online status', async () => {
		render(VehicleStatusBadge, {
			status: 'online'
		});

		await expect.element(page.getByText('Online', { exact: true })).toBeInTheDocument();
	});

	it('renders a pulse for an online vehicle by default', async () => {
		render(VehicleStatusBadge, {
			status: 'online'
		});

		await expect.element(page.getByTestId('vehicle-status-pulse')).toBeInTheDocument();
	});

	it('can disable the online pulse', async () => {
		render(VehicleStatusBadge, {
			status: 'online',
			pulse: false
		});

		await expect.element(page.getByTestId('vehicle-status-pulse')).not.toBeInTheDocument();
	});

	it('renders stale status without a pulse', async () => {
		render(VehicleStatusBadge, {
			status: 'stale'
		});

		await expect.element(page.getByText('Stale', { exact: true })).toBeInTheDocument();

		await expect.element(page.getByTestId('vehicle-status-pulse')).not.toBeInTheDocument();
	});

	it('renders offline status without a pulse', async () => {
		render(VehicleStatusBadge, {
			status: 'offline'
		});

		await expect.element(page.getByText('Offline', { exact: true })).toBeInTheDocument();

		await expect.element(page.getByTestId('vehicle-status-pulse')).not.toBeInTheDocument();
	});

	it('uses an emerald indicator for online status', async () => {
		render(VehicleStatusBadge, {
			status: 'online',
			pulse: false
		});

		await expect.element(page.getByTestId('vehicle-status-dot')).toHaveClass('bg-emerald-500');
	});

	it('uses an amber indicator for stale status', async () => {
		render(VehicleStatusBadge, {
			status: 'stale'
		});

		await expect.element(page.getByTestId('vehicle-status-dot')).toHaveClass('bg-amber-500');
	});

	it('uses a slate indicator for offline status', async () => {
		render(VehicleStatusBadge, {
			status: 'offline'
		});

		await expect.element(page.getByTestId('vehicle-status-dot')).toHaveClass('bg-slate-400');
	});

	it('applies a custom class to the badge', async () => {
		render(VehicleStatusBadge, {
			status: 'online',
			class: 'test-status-badge'
		});

		await expect.element(page.getByTestId('vehicle-status-badge')).toHaveClass('test-status-badge');
	});
});
