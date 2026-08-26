-- V3__activity_outbox.sql
-- Transactional outbox for ActivityCreatedEvent.
--
-- ActivityService used to publish to Kafka from inside its @Transactional methods, so a
-- consumer could evaluate a streak before the activity row committed. Events are now
-- inserted here in the same transaction as the activity and drained by OutboxRelay.

CREATE TABLE IF NOT EXISTS activity_outbox (
    id            BIGSERIAL    PRIMARY KEY,
    event_id      UUID         NOT NULL UNIQUE,
    aggregate_id  UUID         NOT NULL,
    partition_key VARCHAR(64)  NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at       TIMESTAMP
);

-- The relay only ever scans PENDING rows. A partial index keeps that scan fast as SENT
-- rows accumulate, and stays near-empty in steady state.
CREATE INDEX IF NOT EXISTS idx_activity_outbox_pending
    ON activity_outbox (id) WHERE status = 'PENDING';
