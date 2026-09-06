import { describe, expect, it } from 'vitest';

import { positionTrailsContact } from './vehicle-position-age';

const contact = new Date('2026-09-06T12:00:00Z');

describe('positionTrailsContact', () => {
	it('says nothing when the position came with the contact', () => {
		expect(positionTrailsContact(contact, new Date('2026-09-06T11:59:30Z'))).toBe(false);
	});

	it('reports a position left far behind by later contact', () => {
		// The case this exists for: a parked car still reporting, whose newest
		// rows are events that carry no coordinates at all.
		expect(positionTrailsContact(contact, new Date('2026-07-05T14:22:00Z'))).toBe(true);
	});

	it('stays quiet when either side is missing', () => {
		expect(positionTrailsContact(contact, null)).toBe(false);
		expect(positionTrailsContact(null, contact)).toBe(false);
	});

	it('stays quiet when either side is unparseable', () => {
		expect(positionTrailsContact(contact, 'not a date')).toBe(false);
	});

	it('accepts the ISO strings the query and the live stream actually carry', () => {
		expect(positionTrailsContact('2026-09-06T12:00:00Z', '2026-09-06T10:00:00Z')).toBe(true);
	});

	it('never reports a position newer than the contact', () => {
		expect(positionTrailsContact(contact, new Date('2026-09-06T12:30:00Z'))).toBe(false);
	});
});
