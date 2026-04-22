-- Allow deleting a problem that still has submissions.
-- Submissions retain their history; problem_id becomes NULL.
ALTER TABLE submissions DROP CONSTRAINT submissions_problem_id_fkey;
ALTER TABLE submissions ALTER COLUMN problem_id DROP NOT NULL;
ALTER TABLE submissions ADD CONSTRAINT submissions_problem_id_fkey
    FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE SET NULL;
