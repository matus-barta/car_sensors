-- Add migration script here
-- Better Auth admin plugin fields.

ALTER TABLE "user"
    ADD COLUMN role TEXT NOT NULL DEFAULT 'user',
    ADD COLUMN banned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ban_reason TEXT,
    ADD COLUMN ban_expires TIMESTAMP;

ALTER TABLE "session"
    ADD COLUMN impersonated_by TEXT;

CREATE INDEX idx_user_role
    ON "user"(role);

CREATE INDEX idx_user_banned
    ON "user"(banned);

-- Singleton row used to serialize initial application setup.
CREATE TABLE application_setup (
    id TEXT PRIMARY KEY,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    CONSTRAINT application_setup_singleton
        CHECK (id = 'global')
);

INSERT INTO application_setup (id)
VALUES ('global');