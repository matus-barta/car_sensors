-- Add migration script here
CREATE TABLE IF NOT EXISTS known_devices (
    device_id TEXT PRIMARY KEY,
    name TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_known_devices_is_active
    ON known_devices(is_active);

-- Optional but recommended if telemetry_samples.device_id already exists
CREATE INDEX IF NOT EXISTS idx_telemetry_samples_device_id
    ON telemetry_samples(device_id);