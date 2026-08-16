import type { StyleSpecification, VectorSourceSpecification } from 'maplibre-gl';

const SHORTBREAD_SOURCE_ID = 'versatiles-shortbread';

const OPENSTREETMAP_ATTRIBUTION =
	'<a href="https://www.openstreetmap.org/copyright" ' +
	'target="_blank" rel="noopener noreferrer">' +
	'© OpenStreetMap contributors</a>';

export async function createOsmMapStyle(
	styleUrl: string,
	vectorTileUrl: string
): Promise<StyleSpecification> {
	const response = await fetch(styleUrl);

	if (!response.ok) {
		throw new Error(
			`Failed to load the OpenStreetMap style: ` + `${response.status} ${response.statusText}`
		);
	}

	const style = (await response.json()) as StyleSpecification;

	const source = style.sources[SHORTBREAD_SOURCE_ID];

	if (!source || source.type !== 'vector') {
		throw new Error(
			`The OpenStreetMap style does not contain the expected ` +
				`"${SHORTBREAD_SOURCE_ID}" vector source.`
		);
	}

	const vectorSource = source as VectorSourceSpecification;

	vectorSource.tiles = [vectorTileUrl];
	vectorSource.attribution = OPENSTREETMAP_ATTRIBUTION;
	vectorSource.scheme = 'xyz';
	vectorSource.minzoom = 0;
	vectorSource.maxzoom = 14;

	delete vectorSource.url;

	return style;
}
