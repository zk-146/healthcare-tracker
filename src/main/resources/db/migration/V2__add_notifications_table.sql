-- V2__add_notifications_table.sql
-- Adds persisted in-app notifications delivered on streak milestone achievements.

CREATE TABLE IF NOT EXISTS notifications (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL REFERENCES users(id),
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id    ON notifications (user_id);
-- Partial index speeds up unread-count queries (filters to unread rows only)
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id) WHERE NOT is_read;
