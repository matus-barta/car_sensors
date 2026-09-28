export interface MapView {
	center: [longitude: number, latitude: number];
	zoom: number;
}

// `[longitude, latitude]`, typed as the generated JSON is read.
export type TimeZoneCenters = Record<string, readonly number[]>;

/*
 * Nothing about the viewer is known, so the whole world, nudged north so it
 * does not open on the empty Atlantic off the coast of Africa at 0,0. Zoom 2
 * is also the map's minimum.
 */
export const WORLD_VIEW: MapView = { center: [0, 20], zoom: 2 };

// Close enough that the country fills most of the view.
const TIME_ZONE_ZOOM = 6;

/*
 * A band of longitude says nothing about latitude; 20 degrees north runs
 * through the most populated band of it.
 */
const OFFSET_LATITUDE = 20;

/**
 * Where the map opens when it has no vehicle to show.
 *
 * Built only from what the browser already tells every page it loads - the
 * time zone it is set to and its UTC offset - so nothing is asked of the
 * viewer and nothing leaves the browser. Each step is used only when the one
 * before it gives nothing:
 *
 * 1. The time zone's principal location, from the IANA time zone database.
 * 2. The UTC offset, as a band of longitude. It moves with daylight saving,
 *    which shifts the band by fifteen degrees - still the right side of the
 *    world.
 * 3. The whole world. An offset of zero lands here too: from a zone that is
 *    not in the table, most often plain `UTC` on a server or in a browser
 *    that hides its zone, zero means unknown rather than London.
 */
export function resolveDefaultView(
	timeZone: string | undefined,
	offsetMinutes: number,
	centers: TimeZoneCenters
): MapView {
	const [longitude, latitude] = (timeZone && centers[timeZone]) || [];

	if (longitude !== undefined && latitude !== undefined) {
		return { center: [longitude, latitude], zoom: TIME_ZONE_ZOOM };
	}

	if (!Number.isFinite(offsetMinutes) || offsetMinutes === 0) {
		return WORLD_VIEW;
	}

	// `getTimezoneOffset()` is minutes behind UTC, and an hour is 15 degrees.
	const offsetLongitude = Math.max(-180, Math.min(180, -offsetMinutes / 4));

	return { center: [offsetLongitude, OFFSET_LATITUDE], zoom: WORLD_VIEW.zoom };
}

/**
 * The default view for this browser. The coordinates are a separate chunk
 * loaded on the first call, so they cost nothing until a map is empty.
 */
export async function getDefaultView(): Promise<MapView> {
	const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
	const offsetMinutes = new Date().getTimezoneOffset();

	try {
		const { centers } = await import('./generated/timezone-centers.json');

		return resolveDefaultView(timeZone, offsetMinutes, centers);
	} catch (error) {
		console.error('Could not load the time zone coordinates:', error);

		return resolveDefaultView(undefined, offsetMinutes, {});
	}
}
