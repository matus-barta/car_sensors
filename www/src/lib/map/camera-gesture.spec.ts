import { describe, expect, it } from 'vitest';

import { isUserCameraGesture } from './camera-gesture';

describe('isUserCameraGesture', () => {
	it('treats an event carrying the originating DOM event as a user gesture', () => {
		expect(isUserCameraGesture({ originalEvent: new Event('wheel') })).toBe(true);
	});

	it('treats an event with no originating DOM event as programmatic', () => {
		expect(isUserCameraGesture({})).toBe(false);
	});

	it('treats an explicitly undefined originating event as programmatic', () => {
		expect(isUserCameraGesture({ originalEvent: undefined })).toBe(false);
	});

	it('treats a missing event as programmatic', () => {
		expect(isUserCameraGesture(undefined)).toBe(false);
	});
});
