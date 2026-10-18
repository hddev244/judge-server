-- API key roles: ADMIN (full), CLIENT (legacy non-admin), GRADER (grading-only, used by an
-- upstream platform to judge the problems it pushes). is_admin stays as a mirror of role = ADMIN
-- so older code paths and tooling keep working.
ALTER TABLE api_keys ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CLIENT';
UPDATE api_keys SET role = 'ADMIN' WHERE is_admin = true;

-- Problems pushed by a GRADER key. external_key is the upstream platform's stable id;
-- owner_key_id scopes every grader operation to the key that created the problem.
ALTER TABLE problems ADD COLUMN external_key VARCHAR(64);
ALTER TABLE problems ADD COLUMN owner_key_id BIGINT REFERENCES api_keys(id) ON DELETE SET NULL;
ALTER TABLE problems ADD COLUMN package_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_problems_owner_external_key
    ON problems(owner_key_id, external_key) WHERE external_key IS NOT NULL;
