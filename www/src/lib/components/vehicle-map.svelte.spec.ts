import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';

import VehicleMap from './vehicle-map.svelte';

describe('VehicleMap', () => {
	it('renders the map container', async () => {
		render(VehicleMap, {
			vehicles: [],
			selectedVehicleId: null
		});

		await expect.element(page.getByTestId('vehicle-map')).toBeInTheDocument();
	});
});
