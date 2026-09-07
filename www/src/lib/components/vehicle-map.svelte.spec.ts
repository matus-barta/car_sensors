import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';

/*
 * `$env/dynamic/public` reads values the server puts into the page during
 * hydration, so importing it in a component rendered on its own throws. The
 * server modules that read `$env/dynamic/private` need nothing like this,
 * because they run in the node project where the module resolves normally -
 * this is only needed here, in the browser.
 *
 * An empty environment is the honest stand-in: it is what an unconfigured
 * deployment produces, and the map falls back to the public OpenStreetMap
 * servers, which is exactly what this test wants.
 */
vi.mock('$env/dynamic/public', () => ({ env: {} }));

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
