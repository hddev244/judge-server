-- Persist truncated program output so clients can show WA diffs.
ALTER TABLE submission_results ADD COLUMN stdout TEXT;
ALTER TABLE submission_results ADD COLUMN stderr TEXT;
