import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';

import VehicleMap from './vehicle-map.svelte';

describe('VehicleMap', () => {
	it('renders the initial loading state', async () => {
		render(VehicleMap, {
			vehicles: [],
			selectedVehicleId: null
		});

		await expect
			.element(page.getByTestId('vehicle-map'))
			.toHaveAttribute('data-map-state', 'loading');

		await expect.element(page.getByTestId('vehicle-map-loading')).toBeInTheDocument();
	});
});
