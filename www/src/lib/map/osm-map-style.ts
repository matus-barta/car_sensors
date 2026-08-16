import type { StyleSpecification, VectorSourceSpecification } from 'maplibre-gl';

const OSM_COLORFUL_STYLE_URL = 'https://vector.openstreetmap.org/styles/shortbread/colorful.json';

const OPENSTREETMAP_ATTRIBUTION =
	'<a href="https://www.openstreetmap.org/copyright" ' +
	'target="_blank" rel="noopener noreferrer">' +
	'© OpenStreetMap contributors</a>';

export async function createOsmMapStyle(vectorTileUrl: string): Promise<StyleSpecification> {
	const response = await fetch(OSM_COLORFUL_STYLE_URL);

	if (!response.ok) {
		throw new Error(
			`Failed to load the OpenStreetMap style: ` + `${response.status} ${response.statusText}`
		);
	}

	const style = (await response.json()) as StyleSpecification;

	for (const source of Object.values(style.sources)) {
		if (source.type !== 'vector') {
			continue;
		}

		const vectorSource = source as VectorSourceSpecification;

		vectorSource.tiles = [vectorTileUrl];
		vectorSource.attribution = OPENSTREETMAP_ATTRIBUTION;
		vectorSource.scheme = 'xyz';
		vectorSource.minzoom = 0;
		vectorSource.maxzoom = 14;

		delete vectorSource.url;
	}

	return style;
}
