// What a task's frontmatter may say. The tasks collection validates every file
// in docs/tasks/ against these, and the board lays its columns out in this
// order, so a value the board does not know about cannot reach it.

/** Board columns, left to right. */
export const taskStatuses = ['next', 'in-progress', 'blocked', 'backlog'] as const;

export const taskStatusLabels: Record<TaskStatus, string> = {
	next: 'Next',
	'in-progress': 'In progress',
	blocked: 'Blocked',
	backlog: 'Backlog'
};

export const taskAreas = [
	'www',
	'ingest',
	'android',
	'protocol',
	'device-auth',
	'database',
	'ci',
	'docs',
	'trips',
	'geocoding',
	'distribution',
	'tools'
] as const;

export type TaskStatus = (typeof taskStatuses)[number];
export type TaskArea = (typeof taskAreas)[number];
