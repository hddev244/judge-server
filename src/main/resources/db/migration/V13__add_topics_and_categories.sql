CREATE TABLE topics (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE problem_topics (
    problem_id  BIGINT NOT NULL REFERENCES problems(id)  ON DELETE CASCADE,
    topic_id    BIGINT NOT NULL REFERENCES topics(id)    ON DELETE CASCADE,
    PRIMARY KEY (problem_id, topic_id)
);

CREATE TABLE problem_categories (
    problem_id  BIGINT NOT NULL REFERENCES problems(id)     ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id)   ON DELETE CASCADE,
    PRIMARY KEY (problem_id, category_id)
);

CREATE INDEX idx_problem_topics_topic       ON problem_topics(topic_id);
CREATE INDEX idx_problem_categories_category ON problem_categories(category_id);
