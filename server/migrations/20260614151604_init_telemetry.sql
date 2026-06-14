-- Add migration script here
-- telemetry_samples table

CREATE TABLE telemetry_samples (
    -- internal DB id (safe primary key)
    db_id BIGSERIAL PRIMARY KEY,

    -- client-side id (from Android)
    id BIGINT,

    -- device identifier (from header)
    device_id TEXT,

    -- metadata
    event TEXT NOT NULL,
    timestamp BIGINT NOT NULL,

    -- optional JSON payload (stored as TEXT to match your Rust model)
    payload TEXT,

    -- power
    charging BOOLEAN,
    power_source TEXT,

    -- GPS
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    altitude DOUBLE PRECISION,
    speed_mps REAL,
    speed_kmh REAL,
    bearing REAL,
    accuracy_m REAL,
    provider TEXT,

    -- accelerometer
    accel_x REAL,
    accel_y REAL,
    accel_z REAL,
    accel_accuracy INTEGER,
    accel_accuracy_label TEXT,

    -- gyroscope
    gyro_x REAL,
    gyro_y REAL,
    gyro_z REAL,
    gyro_accuracy INTEGER,
    gyro_accuracy_label TEXT,

    -- magnetometer
    mag_x REAL,
    mag_y REAL,
    mag_z REAL,
    magnet_accuracy INTEGER,
    magnet_accuracy_label TEXT,

    -- orientation
    heading_deg REAL,

    -- upload tracking (future use)
    uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at BIGINT,
    upload_attempt_count INTEGER NOT NULL DEFAULT 0
);


CREATE UNIQUE INDEX idx_telemetry_device_id_unique
ON telemetry_samples(device_id, id);

-- time queries
CREATE INDEX idx_telemetry_timestamp
    ON telemetry_samples(timestamp);

-- device + time (very important for your app)
CREATE INDEX idx_telemetry_device_timestamp
    ON telemetry_samples(device_id, timestamp);

-- upload worker queries
CREATE INDEX idx_telemetry_uploaded
    ON telemetry_samples(uploaded);

-- optional: faster upload batching
CREATE INDEX idx_telemetry_uploaded_timestamp
    ON telemetry_samples(uploaded, timestamp);