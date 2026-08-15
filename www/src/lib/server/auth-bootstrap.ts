import { randomUUID } from 'node:crypto';

/**
 * Process-local token used to distinguish the trusted setup action from
 * requests sent directly to Better Auth's public sign-up endpoint.
 *
 * The value is generated at startup and never sent to a browser.
 */
export const AUTH_SETUP_HEADER = 'x-car-sensors-internal-setup';
export const AUTH_SETUP_TOKEN = randomUUID();
