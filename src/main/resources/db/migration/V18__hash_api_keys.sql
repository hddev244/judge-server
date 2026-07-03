-- Replace plaintext API keys with SHA-256 hashes.
-- key_prefix keeps the first 8 chars so admins can still identify keys.
ALTER TABLE api_keys ADD COLUMN key_hash CHAR(64);
ALTER TABLE api_keys ADD COLUMN key_prefix VARCHAR(8);

UPDATE api_keys
SET key_hash   = encode(sha256(key::bytea), 'hex'),
    key_prefix = left(key, 8);

ALTER TABLE api_keys ALTER COLUMN key_hash SET NOT NULL;
ALTER TABLE api_keys ALTER COLUMN key_prefix SET NOT NULL;
ALTER TABLE api_keys ADD CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash);

ALTER TABLE api_keys DROP COLUMN key;
