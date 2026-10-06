/*
 * Runs a test command against browsers in Playwright's own Docker image.
 *
 *     node scripts/with-browsers.ts vitest --run
 *
 * The browsers live in the image, with the system libraries they need, and
 * nothing else does: the command itself - Vitest or Playwright Test, the
 * preview server, the SQLx CLI, Postgres - runs here, on this machine or the
 * CI runner alike. It connects to `playwright run-server` in the container,
 * whose address it finds in PLAYWRIGHT_WS_ENDPOINT, and both configs route
 * the browser's localhost back here. This is the setup Vitest documents:
 * https://vitest.dev/config/browser/playwright
 *
 * The container is left running and reused by the next run, so a machine
 * pays for the image once per Playwright version. `pnpm test:browsers:stop`
 * removes it; a stopped or outdated one is replaced on the next run.
 *
 * PLAYWRIGHT_LOCAL_BROWSERS=1 skips all of this and uses browsers installed
 * on this machine instead (`pnpm exec playwright install chromium`) - for
 * --headed, --debug or anything else that needs a window, which a browser in
 * a container cannot open.
 */

import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { setTimeout as sleep } from 'node:timers/promises';

const container = 'car-sensors-playwright';
const port = 6677;
const startupTimeoutMs = 300_000;

const [command, ...args] = process.argv.slice(2);

if (!command) {
	console.error('Usage: node scripts/with-browsers.ts <command> [args...]');
	process.exit(2);
}

const env = { ...process.env };

if (process.env.PLAYWRIGHT_LOCAL_BROWSERS !== '1') {
	await ensureBrowserServer();
	env.PLAYWRIGHT_WS_ENDPOINT = `ws://127.0.0.1:${port}/`;
}

const child = spawn(command, args, { stdio: 'inherit', env });

child.on('exit', (code, signal) => process.exit(code ?? (signal ? 1 : 0)));

/** Starts the browser server for the installed Playwright, unless it is already running. */
async function ensureBrowserServer(): Promise<void> {
	/*
	 * The image whose browsers this Playwright looks for: with any other
	 * version it cannot find them. Read from what was installed, so a
	 * dependency update moves the image with it.
	 */
	const { version } = JSON.parse(
		readFileSync(new URL('../node_modules/playwright/package.json', import.meta.url), 'utf8')
	) as { version: string };
	const image = `mcr.microsoft.com/playwright:v${version}-noble`;

	const running = docker(['ps', '--filter', `name=^${container}$`, '--format', '{{.Image}}']);

	if (running === image) {
		return;
	}

	// Stopped, or another version's: either way, a fresh one.
	docker(['rm', '--force', container]);

	console.log(`Starting the browsers in ${image} - the first time, that includes pulling it.`);

	// As Playwright's and Vitest's Docker guides run it.
	docker([
		'run',
		'--detach',
		'--name',
		container,
		'--init',
		'--ipc=host',
		'--user',
		'pwuser',
		'--workdir',
		'/home/pwuser',
		'--publish',
		`127.0.0.1:${port}:${port}`,
		image,
		'/bin/sh',
		'-c',
		`npx -y playwright@${version} run-server --port ${port} --host 0.0.0.0`
	]);

	/*
	 * The published port accepts connections as soon as the container
	 * starts, before the server inside is listening, so the server's own
	 * word is what counts.
	 */
	const deadline = Date.now() + startupTimeoutMs;

	while (!containerLogs().includes('Listening on')) {
		// Not removed on exit, so that this can say why.
		if (!docker(['ps', '--quiet', '--filter', `name=^${container}$`])) {
			throw new Error(`The browser server in ${container} stopped:\n${containerLogs()}`);
		}

		if (Date.now() > deadline) {
			throw new Error(`The browser server in ${container} did not start in time.`);
		}

		await sleep(500);
	}
}

/** Both of the container's streams: which one the server reports on is its own business. */
function containerLogs(): string {
	const { stdout = '', stderr = '' } = spawnSync('docker', ['logs', container], {
		encoding: 'utf8'
	});

	return stdout + stderr;
}

function docker(dockerArgs: string[]): string {
	try {
		return execFileSync('docker', dockerArgs, {
			encoding: 'utf8',
			stdio: ['ignore', 'pipe', 'pipe']
		}).trim();
	} catch (error) {
		// Removing a container that does not exist is not a failure.
		if (dockerArgs[0] === 'rm') {
			return '';
		}

		console.error(
			'The browsers run in Docker, and Docker did not answer. Start it, or set ' +
				'PLAYWRIGHT_LOCAL_BROWSERS=1 to use browsers installed on this machine.'
		);
		throw error;
	}
}
