import { getCollection, getEntry } from 'astro:content';
import type { TaskArea, TaskStatus } from './tasks.ts';

export type BoardTask = {
	id: string;
	title: string;
	status: TaskStatus;
	area: TaskArea;
	/** The tasks this one waits for. */
	dependsOn: { id: string; title: string }[];
	/** The tasks waiting for this one. */
	unblocks: { id: string; title: string }[];
};

/**
 * Every task, with its dependencies resolved in both directions.
 *
 * The collection's schema checks that a dependency is shaped like a task id
 * but not that the task exists - reference() only resolves when asked. Asking
 * here, for every one, turns a dependency on a renamed or deleted task into a
 * failed build that names both.
 */
export async function loadTasks(): Promise<BoardTask[]> {
	const entries = (await getCollection('tasks')).sort((a, b) =>
		a.data.title.localeCompare(b.data.title)
	);

	const tasks = new Map<string, BoardTask>(
		entries.map((entry) => [
			entry.id,
			{
				id: entry.id,
				title: entry.data.title,
				status: entry.data.status,
				area: entry.data.area,
				dependsOn: [],
				unblocks: []
			}
		])
	);

	for (const entry of entries) {
		const task = tasks.get(entry.id)!;

		for (const dependency of entry.data.depends_on) {
			const target = await getEntry(dependency);

			if (!target) {
				throw new Error(
					`docs/tasks/${entry.id}.md depends on "${dependency.id}", and there is no docs/tasks/${dependency.id}.md`
				);
			}

			task.dependsOn.push({ id: target.id, title: target.data.title });
			tasks.get(target.id)!.unblocks.push({ id: task.id, title: task.title });
		}
	}

	return [...tasks.values()];
}
