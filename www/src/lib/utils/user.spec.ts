import { describe, expect, it } from 'vitest';

import { getInitials } from './user';

describe('getInitials', () => {
	it('returns initials from the first two name parts', () => {
		expect(getInitials('Alex Morgan')).toBe('AM');
	});

	it('returns one initial for a single name', () => {
		expect(getInitials('Taylor')).toBe('T');
	});

	it('normalizes whitespace', () => {
		expect(getInitials('  Jordan   Lee  ')).toBe('JL');
	});

	it('uses the default fallback for an empty name', () => {
		expect(getInitials('')).toBe('U');
	});

	it('uses a custom fallback when provided', () => {
		expect(getInitials('', '?')).toBe('?');
	});
});
