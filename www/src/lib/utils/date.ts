export function formatRelativeTime(
	value: Date | string | number | null | undefined,
	now = Date.now()
): string | null {
	if (value === null || value === undefined || value === '') {
		return null;
	}

	const date = value instanceof Date ? value : new Date(value);

	if (Number.isNaN(date.getTime())) {
		return null;
	}

	const elapsedSeconds = Math.max(0, Math.floor((now - date.getTime()) / 1000));

	if (elapsedSeconds < 60) {
		return `${elapsedSeconds}s ago`;
	}

	const elapsedMinutes = Math.floor(elapsedSeconds / 60);

	if (elapsedMinutes < 60) {
		return `${elapsedMinutes}m ago`;
	}

	const elapsedHours = Math.floor(elapsedMinutes / 60);

	if (elapsedHours < 24) {
		return `${elapsedHours}h ago`;
	}

	return date.toLocaleDateString();
}
