// The documentation site. The pages themselves are the Markdown files in docs/,
// one directory up, so they stay readable on GitHub without going through this
// project - see src/content.config.ts for how they are loaded.

import { unified } from '@astrojs/markdown-remark';
import starlight from '@astrojs/starlight';
import { defineConfig } from 'astro/config';
import mermaid from 'astro-mermaid';
import starlightLinksValidator from 'starlight-links-validator';
import starlightOpenAPI, { openAPISidebarGroups } from 'starlight-openapi';
import { repoUrl } from './src/paths.ts';
import { rehypeMarkdownLinks } from './src/plugins/rehype-markdown-links.ts';

const base = '/car_sensors';

export default defineConfig({
	site: 'https://matus-barta.github.io',
	base,
	markdown: {
		// unified() rather than Astro's default Sätteri, because astro-mermaid and
		// the link rewriting are rehype plugins, which Sätteri does not run.
		processor: unified({
			rehypePlugins: [[rehypeMarkdownLinks, { base }]],
			// Off so a page reads as it does on GitHub, which does not curl quotes
			// or turn hyphens into dashes.
			smartypants: false
		})
	},
	integrations: [
		// Before Starlight, which would otherwise render the blocks as code.
		mermaid({ autoTheme: true }),
		starlight({
			title: 'car_sensors',
			social: [{ icon: 'github', label: 'GitHub', href: repoUrl }],
			// Starlight appends each page's path relative to this project, which for
			// the pages in docs/ starts with ../ - so the base is this directory.
			editLink: { baseUrl: `${repoUrl}/edit/main/docs/starlight/` },
			markdown: {
				// The docs collection lives outside src/content/docs.
				processedDirs: ['..']
			},
			plugins: [
				starlightLinksValidator(),
				starlightOpenAPI([
					{
						// Generated from the ingest source; see ingest/README.md.
						base: 'api',
						schema: '../api/openapi.json',
						sidebar: { label: 'Ingest API', operations: { badges: true } }
					}
				])
			],
			// The same groups as the landing page, docs/README.md.
			sidebar: [
				// One folder of docs/ per group, so the sidebar and the directory read
				// the same; a folder's README is the group's first page.
				{ label: 'Overview', items: ['overview/architecture', 'overview/android-app'] },
				{ label: 'Deployment', items: ['deployment', 'deployment/www-docker-image'] },
				{
					label: 'Development',
					items: [
						'development',
						'development/ci',
						'development/conventions',
						'development/writing-documentation',
						'development/writing-agent-instructions',
						'development/ai-policy',
						{
							label: 'Database',
							items: ['development/database/ownership', 'development/database/migrations']
						},
						// What applies to one piece only.
						{ label: 'Android', items: ['development/android', 'development/android/build'] },
						{ label: 'ingest', items: ['development/ingest/architecture'] },
						{ label: 'www', items: ['development/www/architecture', 'development/www/testing'] }
					]
				},
				{
					label: 'Reference',
					items: [
						{
							label: 'Database schema',
							collapsed: true,
							items: [{ label: 'Overview', slug: 'schema' }, { autogenerate: { directory: 'schema' } }]
						},
						...openAPISidebarGroups
					]
				},
				{
					label: 'Open work',
					collapsed: true,
					items: [
						{ label: 'Board', link: '/tasks/board/' },
						{ label: 'Task format', slug: 'tasks' },
						{ autogenerate: { directory: 'tasks' } }
					]
				}
			]
		})
	]
});
