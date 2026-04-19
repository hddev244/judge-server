ALTER TABLE problems ADD COLUMN difficulty VARCHAR(10);

CREATE TABLE problem_tags (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL,
    UNIQUE(problem_id, tag)
);
CREATE INDEX idx_problem_tags_problem_id ON problem_tags(problem_id);
CREATE INDEX idx_problem_tags_tag        ON problem_tags(tag);
