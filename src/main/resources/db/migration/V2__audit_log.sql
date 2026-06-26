-- V2__audit_log.sql
-- Compliance audit trail: records security- and data-relevant events
-- (authentication, profile/activity access and changes, data export, account deletion).
-- user_id is nullable because some events (e.g. a failed login for an unknown email)
-- have no associated user.

CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID,
    event_type  VARCHAR(50)  NOT NULL,
    ip_address  VARCHAR(45),
    detail      TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_created ON audit_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_event_type   ON audit_log (event_type);
