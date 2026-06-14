-- Add migration script here

ALTER TABLE telemetry_samples
    ADD COLUMN IF NOT EXISTS pressure_hpa REAL,
    ADD COLUMN IF NOT EXISTS pressure_accuracy INTEGER,
    ADD COLUMN IF NOT EXISTS pressure_accuracy_label TEXT;
