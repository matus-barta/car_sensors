import { describe, expect, it } from 'vitest';

import { resolveDefaultView, WORLD_VIEW, type TimeZoneCenters } from './default-view';
import { centers } from './generated/timezone-centers.json';

const bratislava: [number, number] = [17.12, 48.15];

describe('resolveDefaultView', () => {
	it("frames the time zone's principal location", () => {
		expect(
			resolveDefaultView('Europe/Bratislava', -120, { 'Europe/Bratislava': bratislava })
		).toEqual({ center: bratislava, zoom: 6 });
	});

	it('falls back to a band of longitude for a zone missing from the table', () => {
		expect(resolveDefaultView('Mars/Olympus_Mons', -120, {})).toEqual({
			center: [30, 20],
			zoom: 2
		});
	});

	it('places a zone behind UTC west of Greenwich', () => {
		expect(resolveDefaultView(undefined, 300, {})).toEqual({ center: [-75, 20], zoom: 2 });
	});

	it('keeps an extreme offset on the map', () => {
		expect(resolveDefaultView(undefined, -840, {}).center[0]).toBe(180);
	});

	it('shows the whole world when a zone outside the table is at UTC', () => {
		expect(resolveDefaultView('UTC', 0, {})).toEqual(WORLD_VIEW);
	});

	it('shows the whole world when nothing is known', () => {
		expect(resolveDefaultView(undefined, Number.NaN, {})).toEqual(WORLD_VIEW);
	});
});

describe('generated time zone centers', () => {
	const table: TimeZoneCenters = centers;

	it('covers every time zone this runtime can report', () => {
		const missing = Intl.supportedValuesOf('timeZone').filter((zone) => !(zone in table));

		expect(missing).toEqual([]);
	});

	it('keeps the aliases older browsers report', () => {
		expect(table['Asia/Calcutta']).toEqual(table['Asia/Kolkata']);
	});

	it('stores every center as a valid longitude and latitude', () => {
		const invalid = Object.entries(table).filter(
			([, [longitude, latitude]]) => !(Math.abs(longitude) <= 180 && Math.abs(latitude) <= 90)
		);

		expect(invalid).toEqual([]);
	});
});
