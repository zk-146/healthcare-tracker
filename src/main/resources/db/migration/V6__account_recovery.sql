-- V6__account_recovery.sql
-- Password reset and email verification.
--
-- email_verified starts FALSE for every existing account: none of them has ever proved it
-- controls its address, and saying otherwise would be a guess. Verification does not gate
-- login, so nobody is locked out by this; an existing user verifies from their profile (or
-- implicitly by completing a password reset, which proves the same thing).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- One table for both kinds of emailed, single-use link. Only the SHA-256 of the token is
-- stored, as with refresh_tokens: a database leak must not hand out working reset links.
CREATE TABLE IF NOT EXISTS account_tokens (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL REFERENCES users(id),
    purpose    VARCHAR(32)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_account_tokens_user_purpose ON account_tokens (user_id, purpose);
CREATE INDEX IF NOT EXISTS idx_account_tokens_expires_at   ON account_tokens (expires_at);
