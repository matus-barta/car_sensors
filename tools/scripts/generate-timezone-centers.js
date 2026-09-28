// Regenerates the time zone coordinates the vehicle map opens on when it has
// no vehicle to show. Run from the repository root:
//
//     node tools/scripts/generate-timezone-centers.js
//
// The coordinates are those of each zone's principal location in the IANA
// time zone database's `zone.tab`. Aliases from its `backward` file are added
// for names a browser may still report, such as `Asia/Calcutta`. The output is
// committed; rerun this when a tzdata release adds or moves a zone.

import { writeFile } from 'node:fs/promises';
import { gunzipSync } from 'node:zlib';

const TZDATA_URL = 'https://data.iana.org/time-zones/tzdata-latest.tar.gz';

const outputPath = new URL(
    '../../www/src/lib/map/generated/timezone-centers.json',
    import.meta.url
);

const response = await fetch(TZDATA_URL);

if (!response.ok) {
    throw new Error(`Could not download ${TZDATA_URL}: ${response.status}`);
}

const files = readTar(gunzipSync(Buffer.from(await response.arrayBuffer())));

const version = requireFile(files, 'version').trim();
const centers = {};

for (const line of requireFile(files, 'zone.tab').split('\n')) {
    if (!line || line.startsWith('#')) {
        continue;
    }

    const [, coordinates, zone] = line.split('\t');

    centers[zone] = parseCoordinates(coordinates);
}

for (const line of requireFile(files, 'backward').split('\n')) {
    const [kind, target, alias] = line.replace(/#.*/, '').trim().split(/\s+/);

    if (kind === 'Link' && centers[target] && !centers[alias]) {
        centers[alias] = centers[target];
    }
}

// One zone per line, so a tzdata update reads as a diff of the zones it changed.
const lines = Object.entries(centers)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([zone, center]) => `\t\t${JSON.stringify(zone)}: ${JSON.stringify(center)}`);

await writeFile(
    outputPath,
    `{\n\t"tzdata": ${JSON.stringify(version)},\n\t"centers": {\n${lines.join(',\n')}\n\t}\n}\n`
);

console.info(`Wrote ${lines.length} zones from tzdata ${version}`);

/**
 * `+DDMM+DDDMM` or `+DDMMSS+DDDMMSS`, latitude first, as `zone.tab` writes
 * them. Returned as `[longitude, latitude]`, the order MapLibre takes, rounded
 * to two decimals - the map only ever uses them to frame a country.
 */
function parseCoordinates(value) {
    const match = /^([+-]\d{4,6})([+-]\d{5,7})$/.exec(value);

    if (!match) {
        throw new Error(`Unexpected zone.tab coordinates: ${value}`);
    }

    const [, latitude, longitude] = match;

    return [toDegrees(longitude, 3), toDegrees(latitude, 2)];
}

function toDegrees(value, degreeDigits) {
    const sign = value.startsWith('-') ? -1 : 1;
    const digits = value.slice(1);

    const degrees = Number(digits.slice(0, degreeDigits));
    const minutes = Number(digits.slice(degreeDigits, degreeDigits + 2));
    const seconds = Number(digits.slice(degreeDigits + 2) || 0);

    return Math.round(sign * (degrees + minutes / 60 + seconds / 3600) * 100) / 100;
}

/**
 * Reads the regular files out of an uncompressed tar archive. tzdata's is
 * small and flat, so this skips everything a general reader would handle.
 */
function readTar(archive) {
    const files = new Map();
    let offset = 0;

    while (offset + 512 <= archive.length) {
        const header = archive.subarray(offset, offset + 512);
        const name = header.subarray(0, 100).toString('utf8').replace(/\0.*$/s, '');

        if (!name) {
            break;
        }

        const size = parseInt(header.subarray(124, 136).toString('utf8').trim(), 8) || 0;
        const type = String.fromCharCode(header[156]);

        if (type === '0' || type === '\0') {
            files.set(name, archive.subarray(offset + 512, offset + 512 + size).toString('utf8'));
        }

        offset += 512 + Math.ceil(size / 512) * 512;
    }

    return files;
}

function requireFile(files, name) {
    const content = files.get(name);

    if (content === undefined) {
        throw new Error(`tzdata archive has no ${name}`);
    }

    return content;
}
