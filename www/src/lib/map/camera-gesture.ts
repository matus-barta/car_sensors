/** The part of a MapLibre camera event that says where the movement came from. */
export interface CameraEventOrigin {
	originalEvent?: unknown;
}

/**
 * Whether a camera event was caused by the user rather than by one of the
 * map component's own `easeTo`/`jumpTo` calls.
 *
 * Both raise the same `movestart`/`dragstart`/`zoomstart` events, so the only
 * thing separating them is `originalEvent`: MapLibre attaches the underlying
 * DOM event whenever a gesture caused the movement - a drag, a wheel, a key
 * press, or one of the navigation control's own buttons - and leaves it
 * undefined for a camera method that was not handed one. Reading that is what
 * keeps the follow state honest without a manual flag around every call site
 * that moves the camera.
 */
export function isUserCameraGesture(event: CameraEventOrigin | null | undefined): boolean {
	return event?.originalEvent != null;
}
