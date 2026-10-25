-- V6__activity_goals.sql
-- Adds user-defined activity goals: a metric + target + period the user sets for
-- themselves, scored against the aggregates SummaryService already computes.
--
-- Complements streak_milestones (V1), which tracks externally-defined, all-or-nothing
-- achievements. A goal is the forgiving counterpart: partial progress still counts.

-- ============================================================
-- Activity Goals
-- ============================================================
CREATE TABLE IF NOT EXISTS activity_goals (
    id           UUID             PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID             NOT NULL REFERENCES users(id),
    metric       VARCHAR(20)      NOT NULL,
    period       VARCHAR(10)      NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    active       BOOLEAN          NOT NULL DEFAULT TRUE,
    version      BIGINT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP        NOT NULL DEFAULT now()
);

-- A user may hold only one *active* goal per metric+period, so "150 weekly minutes"
-- cannot exist three times over with conflicting targets. Retired goals (active=false)
-- are deliberately left unconstrained: that is what lets a user archive a weekly-minutes
-- goal and then set a new one. Partial unique index, same device as
-- uk_activities_user_external_id in V2.
CREATE UNIQUE INDEX IF NOT EXISTS uk_activity_goals_user_metric_period
    ON activity_goals (user_id, metric, period)
    WHERE active;

CREATE INDEX IF NOT EXISTS idx_activity_goals_user_id ON activity_goals (user_id);
