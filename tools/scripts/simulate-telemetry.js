#!/usr/bin/env node
/**
 * Uploads synthetic telemetry to a running `ingest` instance, so live vehicle
 * tracking in `www` can be observed without a real device.
 *
 * `X-Device-ID` is both the device's name and its credential (see
 * `todo.md`'s "Device authentication" note): `ingest` only accepts uploads
 * from a device already present in `known_devices`, and there is no endpoint
 * to register one. Add a vehicle from the web app's "Add vehicle" dialog
 * first, copy the device ID it shows, and pass it here with `--device-id`.
 *
 * Examples:
 *   node tools/scripts/simulate-telemetry.js --device-id <id-from-the-web-app>
 *   node tools/scripts/simulate-telemetry.js --device-id <id> --replay samples.json --loop
 *
 * Run with --help for the full option list.
 */

import { readFile } from 'node:fs/promises';

/** Stop retrying once uploads fail this many times in a row - see the main loop. */
const MAX_CONSECUTIVE_FAILURES = 5;

const DEFAULTS = {
	url: 'http://localhost:3000',
	deviceId: null,
	// Bratislava Airport's runway, end to end - not load-bearing, just a fun
	// straight line to drive up and down.
	from: { latitude: 48.17569420303661, longitude: 17.204085262054214 },
	to: { latitude: 48.15605141203635, longitude: 17.23450198122437 },
	speedKmh: 50,
	intervalMs: 2000,
	count: 0,
	replayFile: null,
	loop: false
};

function printHelp() {
	console.log(`Uploads synthetic telemetry to ingest, to observe live vehicle tracking in www.

Usage:
  node tools/scripts/simulate-telemetry.js --device-id <id> [options]

Options:
  --device-id <id>       device id to upload as - add a vehicle from the web
                         app first and copy the device ID it was given
  --url <url>            ingest base URL (default: ${DEFAULTS.url})
  --from <lat,lon>       one end of the route (default: ${DEFAULTS.from.latitude},${DEFAULTS.from.longitude})
  --to <lat,lon>         the other end (default: ${DEFAULTS.to.latitude},${DEFAULTS.to.longitude})
  --speed <kmh>          simulated ground speed (default: ${DEFAULTS.speedKmh})
  --interval <ms>        delay between uploads (default: ${DEFAULTS.intervalMs})
  --count <n>            number of samples to send, 0 = unbounded (default: ${DEFAULTS.count})
  --replay <file>        replay a JSON array of samples (each needs at least
                         "latitude" and "longitude") instead of driving
                         --from/--to
  --loop                 with --replay, start over once the file is exhausted
  --help                 show this help

Without --replay, the vehicle drives the straight line between --from and
--to, at a constant --speed, reversing direction at each end. Stop with
Ctrl+C.`);
}

function parseArgs(argv) {
	const options = {
		...DEFAULTS,
		from: { ...DEFAULTS.from },
		to: { ...DEFAULTS.to }
	};

	for (let i = 0; i < argv.length; i += 1) {
		const arg = argv[i];

		switch (arg) {
			case '--url':
				options.url = requireValue(argv, ++i, arg);
				break;
			case '--device-id':
				options.deviceId = requireValue(argv, ++i, arg);
				break;
			case '--from':
				options.from = requireCoordinates(argv, ++i, arg);
				break;
			case '--to':
				options.to = requireCoordinates(argv, ++i, arg);
				break;
			case '--speed':
				options.speedKmh = requireNumber(argv, ++i, arg);
				break;
			case '--interval':
				options.intervalMs = requireNumber(argv, ++i, arg);
				break;
			case '--count':
				options.count = requireNumber(argv, ++i, arg);
				break;
			case '--replay':
				options.replayFile = requireValue(argv, ++i, arg);
				break;
			case '--loop':
				options.loop = true;
				break;
			case '--help':
			case '-h':
				printHelp();
				process.exit(0);
				break;
			default:
				fail(`Unknown option: ${arg}`);
		}
	}

	if (!options.deviceId) {
		fail(
			'--device-id is required - add a vehicle from the web app and pass the device ID it was given.'
		);
	}

	return options;
}

function requireValue(argv, index, flag) {
	const value = argv[index];

	if (value === undefined) {
		fail(`${flag} requires a value`);
	}

	return value;
}

function requireNumber(argv, index, flag) {
	const value = Number(requireValue(argv, index, flag));

	if (!Number.isFinite(value)) {
		fail(`${flag} expects a number, got: ${argv[index]}`);
	}

	return value;
}

function requireCoordinates(argv, index, flag) {
	const [latitude, longitude] = requireValue(argv, index, flag)
		.split(',')
		.map(Number);

	if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
		fail(`${flag} expects "lat,lon", got: ${argv[index]}`);
	}

	return { latitude, longitude };
}

function fail(message) {
	console.error(message);
	process.exit(1);
}

const EARTH_RADIUS_METERS = 6_371_000;

function toRadians(degrees) {
	return (degrees * Math.PI) / 180;
}

function toDegrees(radians) {
	return (radians * 180) / Math.PI;
}

function haversineDistanceMeters(from, to) {
	const lat1 = toRadians(from.latitude);
	const lat2 = toRadians(to.latitude);
	const deltaLat = toRadians(to.latitude - from.latitude);
	const deltaLon = toRadians(to.longitude - from.longitude);

	const a =
		Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;

	return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
}

/** Compass bearing (0-360, clockwise from north) of the straight line from `from` to `to`. */
function bearingBetween(from, to) {
	const lat1 = toRadians(from.latitude);
	const lat2 = toRadians(to.latitude);
	const deltaLon = toRadians(to.longitude - from.longitude);

	const y = Math.sin(deltaLon) * Math.cos(lat2);
	const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

	return (toDegrees(Math.atan2(y, x)) + 360) % 360;
}

function lerp(from, to, fraction) {
	return from + (to - from) * fraction;
}

/**
 * A point on the straight line between `options.from` and `options.to`,
 * `elapsedSeconds` after the vehicle started shuttling between them at a
 * constant `options.speedKmh` - reversing direction instantly at each end,
 * like a car repeatedly driving the length of a runway.
 */
function positionOnLine(options, elapsedSeconds) {
	const speedMetersPerSecond = (options.speedKmh * 1000) / 3600;
	const legMeters = haversineDistanceMeters(options.from, options.to);
	const legSeconds = legMeters / speedMetersPerSecond;
	const periodSeconds = legSeconds * 2;

	const cursor = elapsedSeconds % periodSeconds;
	const headingToDestination = cursor < legSeconds;

	// Fraction of the way from `from` to `to`, whichever direction is
	// currently being driven: 0 -> 1 outbound, then 1 -> 0 on the way back.
	const progress = headingToDestination
		? cursor / legSeconds
		: 1 - (cursor - legSeconds) / legSeconds;

	return {
		latitude: lerp(options.from.latitude, options.to.latitude, progress),
		longitude: lerp(options.from.longitude, options.to.longitude, progress),
		bearing: bearingBetween(
			headingToDestination ? options.from : options.to,
			headingToDestination ? options.to : options.from
		)
	};
}

function syntheticSampleSource(options) {
	return function next(index) {
		const elapsedSeconds = (index * options.intervalMs) / 1000;
		const { latitude, longitude, bearing } = positionOnLine(options, elapsedSeconds);

		return {
			done: false,
			sample: {
				id: index + 1,
				event: 'tick',
				timestamp: Date.now(),
				latitude,
				longitude,
				speedKmh: options.speedKmh,
				bearing,
				accuracyM: 8,
				charging: false,
				powerSource: 'battery'
			}
		};
	};
}

async function replaySampleSource(options) {
	const raw = await readFile(options.replayFile, 'utf8');
	const recordings = JSON.parse(raw);

	if (!Array.isArray(recordings) || recordings.length === 0) {
		fail(`${options.replayFile} must contain a non-empty JSON array of samples`);
	}

	return function next(index) {
		if (!options.loop && index >= recordings.length) {
			return { done: true };
		}

		const recording = recordings[index % recordings.length];

		return {
			done: false,
			// `id`/`event`/`timestamp` are supplied if missing rather than
			// required, so a minimal `{ latitude, longitude }` recording works.
			sample: {
				id: index + 1,
				event: 'tick',
				...recording,
				timestamp: Date.now()
			}
		};
	};
}

async function uploadSample(options, sample) {
	const response = await fetch(new URL('/api/telemetry/upload', options.url), {
		method: 'POST',
		headers: {
			'content-type': 'application/json',
			'x-device-id': options.deviceId
		},
		body: JSON.stringify([sample])
	});

	if (!response.ok) {
		const body = await response.text().catch(() => '');

		throw new Error(
			`ingest responded ${response.status} ${response.statusText}${body ? `: ${body}` : ''}`
		);
	}
}

function sleep(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
	const options = parseArgs(process.argv.slice(2));

	const next = options.replayFile
		? await replaySampleSource(options)
		: syntheticSampleSource(options);

	console.log(
		options.replayFile
			? `Replaying ${options.replayFile} for ${options.deviceId} at ${options.url}`
			: `Driving ${options.deviceId} between ${options.from.latitude},${options.from.longitude} ` +
					`and ${options.to.latitude},${options.to.longitude} at ${options.url}`
	);
	console.log('Press Ctrl+C to stop.');

	let stopped = false;

	process.on('SIGINT', () => {
		stopped = true;
	});

	// `tick` drives the route/replay position and always advances, so the
	// simulated vehicle keeps moving even through a failed upload, and is what
	// `--count` caps - counting only `sent` would retry forever at `--interval`
	// against a device that will never be accepted, e.g. a typo'd id.
	let tick = 0;
	let sent = 0;
	let consecutiveFailures = 0;

	while (!stopped && (options.count === 0 || tick < options.count)) {
		const result = next(tick);

		tick += 1;

		if (result.done) {
			console.log('Replay file exhausted.');
			break;
		}

		try {
			await uploadSample(options, result.sample);

			sent += 1;
			consecutiveFailures = 0;

			console.log(
				`#${sent} lat=${result.sample.latitude.toFixed(6)} lon=${result.sample.longitude.toFixed(6)}` +
					(typeof result.sample.bearing === 'number'
						? ` bearing=${result.sample.bearing.toFixed(0)}°`
						: '')
			);
		} catch (cause) {
			consecutiveFailures += 1;

			console.error(`Upload failed: ${cause.message}`);

			if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
				console.error(
					`Stopping after ${consecutiveFailures} failed uploads in a row - is ` +
						`"${options.deviceId}" added as a vehicle in the web app?`
				);
				break;
			}
		}

		if (stopped) {
			break;
		}

		await sleep(options.intervalMs);
	}

	console.log(`Sent ${sent} of ${tick} attempted sample(s).`);
}

await main();
