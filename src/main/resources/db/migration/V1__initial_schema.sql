-- V1__initial_schema.sql
-- Baseline migration: creates all tables, indexes, and constraints
-- for the Healthcare Activity Tracker application.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    date_of_birth DATE,
    gender        VARCHAR(20),
    height_cm     DOUBLE PRECISION,
    weight_kg     DOUBLE PRECISION,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- Activities
-- ============================================================
CREATE TABLE IF NOT EXISTS activities (
    id               UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID           NOT NULL REFERENCES users(id),
    activity_type    VARCHAR(255)   NOT NULL,
    source           VARCHAR(10)    NOT NULL,
    device_id        VARCHAR(100),
    started_at       TIMESTAMP      NOT NULL,
    ended_at         TIMESTAMP,
    duration_minutes INTEGER,
    distance_km      DOUBLE PRECISION,
    calories_burned  DOUBLE PRECISION,
    heart_rate_avg   INTEGER,
    steps            INTEGER,
    notes            TEXT,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_activities_user_date  ON activities (user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_activities_source     ON activities (source);

-- ============================================================
-- Refresh Tokens
-- ============================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user       ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- ============================================================
-- Streak Milestones
-- ============================================================
CREATE TABLE IF NOT EXISTS streak_milestones (
    id                      UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID      NOT NULL REFERENCES users(id),
    milestone_days          INTEGER   NOT NULL,
    achieved_at             TIMESTAMP NOT NULL,
    triggering_activity_id  UUID      NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_streak_milestone_user_days UNIQUE (user_id, milestone_days)
);

CREATE INDEX IF NOT EXISTS idx_streak_milestones_user_id ON streak_milestones (user_id);
