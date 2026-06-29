-- V2__google_health_integration.sql
-- Adds support for importing Fitbit (Charge 6) data via the Google Health API.
--   1. google_health_connections : stores the owner's encrypted OAuth tokens + sync state
--   2. activities.external_id     : source record id, used to de-duplicate imported workouts

-- ============================================================
-- Google Health Connection (one row per linked user)
-- ============================================================
CREATE TABLE IF NOT EXISTS google_health_connections (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID         NOT NULL REFERENCES users(id),
    access_token        TEXT         NOT NULL,
    refresh_token       TEXT         NOT NULL,
    token_expires_at    TIMESTAMP    NOT NULL,
    scopes              TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'CONNECTED',
    last_synced_at      TIMESTAMP,
    connected_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_google_health_connection_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_google_health_connections_user_id
    ON google_health_connections (user_id);

-- ============================================================
-- De-duplication for imported activities
-- ============================================================
ALTER TABLE activities ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

-- A given source record may only be imported once per user. MANUAL activities leave
-- external_id NULL, so a partial unique index keeps them unconstrained.
CREATE UNIQUE INDEX IF NOT EXISTS uk_activities_user_external_id
    ON activities (user_id, external_id)
    WHERE external_id IS NOT NULL;
