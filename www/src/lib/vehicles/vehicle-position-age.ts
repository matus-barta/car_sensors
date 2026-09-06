/**
 * How far a position may trail the last contact before that is worth saying.
 *
 * Matched to the window in which a vehicle still counts as online: inside it,
 * the position and the contact are the same event as far as anyone reading the
 * card is concerned, and saying so twice is noise.
 */
const POSITION_LAG_THRESHOLD_MS = 2 * 60 * 1000;

function toMillis(value: Date | string | null | undefined): number | null {
	if (value === null || value === undefined) {
		return null;
	}

	const millis = value instanceof Date ? value.getTime() : new Date(value).getTime();

	return Number.isFinite(millis) ? millis : null;
}

/**
 * Whether the known position is meaningfully older than the last contact.
 *
 * The two come apart because plenty of rows carry no position - an event, or a
 * sample whose fix had gone stale - so a device can go on reporting while the
 * newest coordinates anyone has stay where it last had a fix. Without this the
 * card would show a vehicle as online beside coordinates from two months ago
 * and give no hint which of the two the reader was looking at.
 */
export function positionTrailsContact(
	lastSeenAt: Date | string | null | undefined,
	positionAt: Date | string | null | undefined,
	thresholdMs = POSITION_LAG_THRESHOLD_MS
): boolean {
	const lastSeen = toMillis(lastSeenAt);
	const position = toMillis(positionAt);

	if (lastSeen === null || position === null) {
		return false;
	}

	return lastSeen - position > thresholdMs;
}
