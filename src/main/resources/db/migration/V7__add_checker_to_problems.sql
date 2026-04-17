ALTER TABLE problems
    ADD COLUMN checker_type     VARCHAR(20)  NOT NULL DEFAULT 'EXACT',
    ADD COLUMN checker_language VARCHAR(10),
    ADD COLUMN checker_source   TEXT,
    ADD COLUMN checker_bin_path VARCHAR(500);
