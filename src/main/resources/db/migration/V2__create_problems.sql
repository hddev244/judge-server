CREATE TABLE problems (
    id               BIGSERIAL PRIMARY KEY,
    slug             VARCHAR(100) NOT NULL UNIQUE,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    time_limit_ms    INT          NOT NULL DEFAULT 2000,
    memory_limit_kb  INT          NOT NULL DEFAULT 262144,
    is_published     BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_problems_slug ON problems(slug);
