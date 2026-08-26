-- V4__activity_outbox_backoff.sql
-- Adds exponential backoff between outbox retry attempts. Without this, a row that keeps
-- failing is retried every poll interval and permanently parked as FAILED after only
-- max-attempts * poll-interval-ms of continuous failure -- far shorter than any real
-- Kafka outage.
--
-- Nullable with no default on purpose: existing and newly inserted PENDING rows have
-- next_attempt_at IS NULL and are immediately eligible, which is exactly today's behaviour
-- for a first attempt.
ALTER TABLE activity_outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;
