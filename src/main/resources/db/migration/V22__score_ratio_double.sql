-- score_ratio maps to a Java Double (float(53)); align the column type.
ALTER TABLE submission_results ALTER COLUMN score_ratio TYPE DOUBLE PRECISION;
