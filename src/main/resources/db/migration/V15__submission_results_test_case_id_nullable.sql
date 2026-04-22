-- Allow deleting test_cases that still have submission_results.
-- Results retain their data; test_case_id becomes NULL.
ALTER TABLE submission_results DROP CONSTRAINT submission_results_test_case_id_fkey;
ALTER TABLE submission_results ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE submission_results ADD CONSTRAINT submission_results_test_case_id_fkey
    FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE SET NULL;
