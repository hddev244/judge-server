-- Partial-credit scoring and float-tolerant output comparison.
ALTER TABLE submission_results ADD COLUMN score_ratio NUMERIC(6,5);

ALTER TABLE problems ADD COLUMN comparison_mode VARCHAR(10) NOT NULL DEFAULT 'EXACT';
ALTER TABLE problems ADD COLUMN float_epsilon DOUBLE PRECISION;
