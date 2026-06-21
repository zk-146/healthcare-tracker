-- V2__add_user_timezone.sql
-- Adds an optional IANA timezone preference to users. Used for streak-boundary
-- calculations in asynchronous milestone detection (where no request-scoped
-- X-User-Timezone header is available). NULL means "treat as UTC".

ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(64);
