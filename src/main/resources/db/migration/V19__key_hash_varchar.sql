-- V18 created key_hash as CHAR(64) (bpchar), which Hibernate schema validation
-- rejects against the VARCHAR entity mapping. Align the column type.
ALTER TABLE api_keys ALTER COLUMN key_hash TYPE VARCHAR(64);
