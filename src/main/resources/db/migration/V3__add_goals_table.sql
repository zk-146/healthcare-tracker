-- Goals: daily fitness targets set by the user
CREATE TABLE goals (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    metric          VARCHAR(50)  NOT NULL,
    target_value    DOUBLE PRECISION NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_achieved_date DATE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_goals_user_id     ON goals (user_id);
CREATE INDEX idx_goals_user_active ON goals (user_id, active);
