import { docsSchema } from '@astrojs/starlight/schema';
import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { pageId } from './paths.ts';

/*
 * Starlight's own docsLoader() is a glob fixed to src/content/docs. The pages
 * here are docs/ itself, one level up, so the same glob is pointed there -
 * leaving out this project, whose node_modules is full of Markdown.
 */
export const collections = {
	docs: defineCollection({
		loader: glob({
			base: '..',
			pattern: ['**/*.{md,mdx}', '!starlight/**'],
			generateId: ({ entry }) => pageId(entry)
		}),
		schema: docsSchema()
	})
};
