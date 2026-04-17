CREATE TABLE subtasks (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT      NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    score       INT         NOT NULL DEFAULT 0,
    order_index INT         NOT NULL DEFAULT 0
);

ALTER TABLE test_cases
    ADD COLUMN subtask_id BIGINT REFERENCES subtasks(id) ON DELETE SET NULL;
