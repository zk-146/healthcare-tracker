-- V3__email_verification_and_tokens.sql
-- Adds email-verification state to users and a generic one-time-token table backing
-- both email verification and password reset flows. Raw tokens are never stored; only
-- their SHA-256 hash is persisted to limit exposure if the database is compromised.

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS one_time_tokens (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    purpose     VARCHAR(30)  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_one_time_tokens_token_hash ON one_time_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_one_time_tokens_user       ON one_time_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_one_time_tokens_expires_at ON one_time_tokens (expires_at);
