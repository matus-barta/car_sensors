-- The device id stops being the credential.
--
-- Until now `X-Device-ID` both named a device and authorised it, so the
-- identity was the secret: it appears on the phone's screen, in the web
-- application, in this table and in service logs, and leaking it meant the
-- only remedy was deactivating the device, which locked the genuine phone out
-- along with whoever had copied it.
--
-- The token separates the two. `www` generates 32 random bytes, shows them
-- once and keeps only this hash; `ingest` hashes what a request presents and
-- compares. Rotating the token leaves `device_id` alone, so a vehicle keeps
-- its whole history when its handset is replaced or its credential withdrawn.
ALTER TABLE known_devices
    ADD COLUMN IF NOT EXISTS token_hash TEXT,
    ADD COLUMN IF NOT EXISTS token_rotated_at TIMESTAMPTZ;

-- Nullable on purpose, and not a compatibility window: a row without a hash
-- cannot be authenticated at all, so devices registered under the old scheme
-- stop uploading until a token is minted for them. `ingest` never accepts an
-- identity on its own, which is the whole point of the change.
COMMENT ON COLUMN known_devices.token_hash IS
    'Lowercase hex SHA-256 of the device token. NULL means the device has no '
    'credential and cannot authenticate.';

COMMENT ON COLUMN known_devices.token_rotated_at IS
    'When the token was last issued or rotated. A record, not an expiry: the '
    'token stays valid until it is rotated again or the device is deactivated.';
