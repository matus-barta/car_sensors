// Rewrites relative links between Markdown files into links the site can follow.
//
// The pages are written to be read on GitHub as much as here, so they link to
// each other as files - `[CI](guides/ci.md#runners-are-pinned)` - which lychee
// checks in CI. On the site that link has to become the page's URL instead, and
// a link to something that is not a page - a workflow, a source file - has to
// go to that file on GitHub. starlight-links-validator then checks every link
// this produces, so a wrong rewrite fails the build rather than shipping.

import type { RehypePlugin } from '@astrojs/markdown-remark';
import path from 'node:path';
import { docsRoot, pageId, repoRoot, repoUrl, siteRoot } from '../paths.ts';

const SCHEME = /^[a-z][a-z\d+.-]*:/i;

type Options = { base?: string };

// Only the parts of the HTML syntax tree and the file this plugin touches.
type Node = { type: string; tagName?: string; properties?: Record<string, unknown>; children?: Node[] };
type MarkdownFile = { path?: string; data: { astro?: { frontmatter?: Record<string, unknown> } } };

export const rehypeMarkdownLinks: RehypePlugin<[Options?]> = ({ base = '' } = {}) => {
	const prefix = base.replace(/\/$/, '');

	return (tree, vfile) => {
		const file = vfile as MarkdownFile;
		const source = file.path;

		if (!source) return;

		announceSlug(file, source);

		walk(tree as Node, (node) => {
			const href = node.properties?.href;

			if (node.tagName !== 'a' || typeof href !== 'string') return;

			const rewritten = rewrite(href, source, prefix);

			if (rewritten && node.properties) node.properties.href = rewritten;
		});
	};
};

/*
 * starlight-links-validator works a page's URL out from its file path, as if
 * the file were in src/content/docs, so for these pages it would get README and
 * dotted names wrong. It takes a `slug` from the frontmatter when there is one,
 * so the page's real id - the same pageId() the collection routes by - is put
 * there. Only the frontmatter seen by the Markdown pipeline changes; the file
 * and the route do not.
 */
function announceSlug(file: MarkdownFile, source: string): void {
	const frontmatter = file.data.astro?.frontmatter;

	if (!frontmatter || frontmatter.slug !== undefined) return;
	if (!isInside(docsRoot, source) || isInside(siteRoot, source)) return;

	const id = pageId(toPosix(path.relative(docsRoot, source)));

	// The validator joins the base and the slug, so the home page needs a slug
	// that adds nothing but the trailing slash its links end with.
	frontmatter.slug = id === 'index' ? './' : id;
}

function rewrite(href: string, sourceFile: string, prefix: string): string | null {
	if (SCHEME.test(href) || href.startsWith('/') || href.startsWith('#')) return null;

	const [, target = '', suffix = ''] = /^([^?#]*)(.*)$/.exec(href) ?? [];

	if (!target) return null;

	const absolute = path.resolve(path.dirname(sourceFile), decodeURIComponent(target));

	if (isInside(docsRoot, absolute) && !isInside(siteRoot, absolute) && /\.mdx?$/.test(absolute)) {
		const id = pageId(toPosix(path.relative(docsRoot, absolute)));

		return `${prefix}/${id === 'index' ? '' : `${id}/`}${suffix}`;
	}

	if (isInside(repoRoot, absolute)) {
		const kind = target.endsWith('/') ? 'tree' : 'blob';

		return `${repoUrl}/${kind}/main/${toPosix(path.relative(repoRoot, absolute))}${suffix}`;
	}

	return null;
}

function isInside(directory: string, file: string): boolean {
	const relative = path.relative(directory, file);

	return relative !== '' && !relative.startsWith('..') && !path.isAbsolute(relative);
}

function toPosix(relative: string): string {
	return relative.split(path.sep).join('/');
}

function walk(node: Node, visit: (node: Node) => void): void {
	if (node.type === 'element') visit(node);

	for (const child of node.children ?? []) walk(child, visit);
}
