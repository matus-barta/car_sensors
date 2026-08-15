import { describe, expect, it } from 'vitest';

import { formatRelativeTime } from './date';

describe('formatRelativeTime', () => {
	const now = new Date('2026-08-15T12:00:00Z').getTime();

	it('formats seconds', () => {
		expect(formatRelativeTime('2026-08-15T11:59:30Z', now)).toBe('30s ago');
	});

	it('formats minutes', () => {
		expect(formatRelativeTime('2026-08-15T11:48:00Z', now)).toBe('12m ago');
	});

	it('formats hours', () => {
		expect(formatRelativeTime('2026-08-15T10:00:00Z', now)).toBe('2h ago');
	});

	it('returns null for an invalid date', () => {
		expect(formatRelativeTime('invalid', now)).toBeNull();
	});

	it('returns null for a missing value', () => {
		expect(formatRelativeTime(null, now)).toBeNull();
	});
});
