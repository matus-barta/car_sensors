import { docsSchema } from '@astrojs/starlight/schema';
import { defineCollection, reference } from 'astro:content';
import { glob } from 'astro/loaders';
import { z } from 'astro/zod';
import { pageId } from './paths.ts';
import { taskAreas, taskStatuses } from './tasks.ts';

/*
 * Starlight's own docsLoader() is a glob fixed to src/content/docs. The pages
 * here are docs/ itself, one level up, so the same glob is pointed there -
 * leaving out this project, whose node_modules is full of Markdown.
 */
const docs = defineCollection({
	loader: glob({
		base: '..',
		pattern: ['**/*.{md,mdx}', '!starlight/**'],
		generateId: ({ entry }) => pageId(entry)
	}),
	schema: docsSchema()
});

/*
 * The same task files, read a second time for what the docs collection does
 * not check: Starlight's schema accepts any extra frontmatter and drops it, so
 * a misspelt status would vanish silently. Here every field is required, no
 * other field is allowed, and each dependency must name a task that exists -
 * any of which fails the build. The board is drawn from this collection.
 */
const tasks = defineCollection({
	loader: glob({
		base: '../tasks',
		pattern: ['*.md', '!README.md'],
		generateId: ({ entry }) => entry.replace(/\.md$/, '')
	}),
	schema: z.strictObject({
		title: z.string().min(1),
		status: z.enum(taskStatuses),
		area: z.enum(taskAreas),
		depends_on: z.array(reference('tasks'))
	})
});

export const collections = { docs, tasks };
