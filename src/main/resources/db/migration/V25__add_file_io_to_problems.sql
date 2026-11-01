-- File I/O problems (Vietnamese olympiad style: read BAI1.INP, write BAI1.OUT).
-- io_mode: STDIO (default) | FILE (only the files count) | FILE_OR_STDIO (either way).
ALTER TABLE problems ADD COLUMN io_mode VARCHAR(16) NOT NULL DEFAULT 'STDIO';
ALTER TABLE problems ADD COLUMN input_file VARCHAR(64);
ALTER TABLE problems ADD COLUMN output_file VARCHAR(64);
