CREATE TABLE test_cases (
    id           BIGSERIAL PRIMARY KEY,
    problem_id   BIGINT       NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    input_path   VARCHAR(500) NOT NULL,
    output_path  VARCHAR(500) NOT NULL,
    is_sample    BOOLEAN      NOT NULL DEFAULT false,
    score        INT          NOT NULL DEFAULT 1,
    order_index  INT          NOT NULL DEFAULT 0
);

CREATE TABLE submission_results (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  VARCHAR(36)  NOT NULL REFERENCES submissions(id),
    test_case_id   BIGINT       NOT NULL REFERENCES test_cases(id),
    status         VARCHAR(20)  NOT NULL,
    time_ms        INT,
    memory_kb      INT
);

CREATE INDEX idx_test_cases_problem_id        ON test_cases(problem_id);
CREATE INDEX idx_submission_results_sub_id    ON submission_results(submission_id);
