CREATE TABLE webhook_retries (
    id               BIGSERIAL PRIMARY KEY,
    submission_id    VARCHAR(36)  NOT NULL,
    callback_url     VARCHAR(500) NOT NULL,
    payload          TEXT         NOT NULL,
    signature        VARCHAR(100) NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 1,
    next_retry_at    TIMESTAMP    NOT NULL,
    failed_permanently BOOLEAN    NOT NULL DEFAULT false,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_retries_next ON webhook_retries(next_retry_at)
    WHERE failed_permanently = false;
