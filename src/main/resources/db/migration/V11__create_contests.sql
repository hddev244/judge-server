CREATE TABLE contests (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    is_public   BOOLEAN NOT NULL DEFAULT false,
    created_by  BIGINT REFERENCES api_keys(id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE contest_problems (
    id           BIGSERIAL PRIMARY KEY,
    contest_id   BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    problem_id   BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    order_index  INT NOT NULL DEFAULT 0,
    alias        VARCHAR(10),
    UNIQUE (contest_id, problem_id)
);

CREATE TABLE contest_participants (
    id            BIGSERIAL PRIMARY KEY,
    contest_id    BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    user_ref      VARCHAR(100) NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (contest_id, user_ref)
);

ALTER TABLE submissions
    ADD COLUMN contest_id BIGINT REFERENCES contests(id) ON DELETE SET NULL;

CREATE INDEX idx_contest_problems_contest_id ON contest_problems(contest_id);
CREATE INDEX idx_contest_participants_contest_id ON contest_participants(contest_id);
CREATE INDEX idx_submissions_contest_id ON submissions(contest_id);
