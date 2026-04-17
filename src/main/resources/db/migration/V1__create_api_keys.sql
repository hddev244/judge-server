CREATE TABLE api_keys (
    id               BIGSERIAL PRIMARY KEY,
    key              VARCHAR(64)  NOT NULL UNIQUE,
    client_name      VARCHAR(100) NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    is_admin         BOOLEAN      NOT NULL DEFAULT false,
    rate_limit_per_hour INT       NOT NULL DEFAULT 100,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_key ON api_keys(key) WHERE is_active = true;
