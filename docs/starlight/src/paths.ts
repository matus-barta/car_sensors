// Where the pages come from, and how a file's path becomes its page id.
//
// The content collection and the link plugin both use pageId(), so a link
// rewritten to a page and the page itself cannot disagree about where it is.

import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** docs/starlight - this project. */
export const siteRoot = fileURLToPath(new URL('..', import.meta.url));

/** docs - the Markdown the site is built from, readable on its own. */
export const docsRoot = path.resolve(siteRoot, '..');

export const repoRoot = path.resolve(docsRoot, '..');

export const repoUrl = 'https://github.com/matus-barta/car_sensors';

/**
 * The page id for a file, given its path relative to docs/ with forward slashes.
 *
 * A README is the index of its directory, as it is on GitHub: `README.md` is
 * the home page and `schema/README.md` is `schema`.
 */
export function pageId(relativePath: string): string {
	const withoutExtension = relativePath.replace(/\.mdx?$/, '');

	if (/^README$/i.test(withoutExtension)) return 'index';

	return withoutExtension.replace(/\/README$/i, '');
}
