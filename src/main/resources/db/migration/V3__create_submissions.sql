CREATE TABLE submissions (
    id            VARCHAR(36)  PRIMARY KEY,
    user_ref      VARCHAR(100),
    problem_id    BIGINT       NOT NULL REFERENCES problems(id),
    language      VARCHAR(10)  NOT NULL,
    source_code   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    score         INT          NOT NULL DEFAULT 0,
    time_ms       INT,
    memory_kb     INT,
    error_message TEXT,
    callback_url  VARCHAR(500),
    client_id     BIGINT       NOT NULL REFERENCES api_keys(id),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMP
);

CREATE INDEX idx_submissions_problem_id ON submissions(problem_id);
CREATE INDEX idx_submissions_client_id  ON submissions(client_id);
CREATE INDEX idx_submissions_status     ON submissions(status);
