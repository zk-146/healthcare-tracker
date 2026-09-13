-- V5__deepseek_api_key.sql
-- Lets a user supply their own DeepSeek API key (Profile > AI settings) so the app can call
-- DeepSeek instead of the local Ollama instance for AI digests/insights.
--
-- A dedicated table, not a column on users: mirrors google_health_connections (V2) rather than
-- the users table itself, since this is the same kind of thing -- a per-user external-service
-- credential -- and should have its own lifecycle (its own row to insert/update/delete) instead
-- of growing the core users table with one column per integration.

CREATE TABLE IF NOT EXISTS deepseek_connections (
    id                 UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id            UUID         NOT NULL REFERENCES users(id),
    api_key_encrypted  TEXT         NOT NULL,
    connected_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_deepseek_connection_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_deepseek_connections_user_id
    ON deepseek_connections (user_id);
