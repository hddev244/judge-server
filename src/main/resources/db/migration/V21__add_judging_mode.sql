-- Early-exit judging policy. checker_type additionally accepts 'INTERACTIVE'
-- (validated in the application layer; no schema change needed for that).
ALTER TABLE problems ADD COLUMN judging_mode VARCHAR(20) NOT NULL DEFAULT 'ALL';
