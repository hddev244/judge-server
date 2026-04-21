package com.judge.repository;

import com.judge.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, String> {
    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.results r LEFT JOIN FETCH r.testCase WHERE s.id = :id")
    Optional<Submission> findByIdWithResults(String id);

    List<Submission> findByContestIdAndIsTestRunFalse(Long contestId);

    @Query(value = """
            SELECT
              user_ref,
              COUNT(DISTINCT problem_id) FILTER (WHERE status = 'AC') AS solved_count,
              COUNT(*)                                                  AS total_submissions,
              ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'AC') / NULLIF(COUNT(*), 0), 1) AS acceptance_rate,
              MAX(finished_at) FILTER (WHERE status = 'AC')            AS last_solved_at
            FROM submissions
            WHERE user_ref IS NOT NULL
              AND is_test_run = false
            GROUP BY user_ref
            ORDER BY solved_count DESC, last_solved_at ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findLeaderboard(@Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT
              COUNT(DISTINCT problem_id) FILTER (WHERE status = 'AC') AS solved_count,
              COUNT(*)                                                  AS total_submissions,
              ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'AC') / NULLIF(COUNT(*), 0), 1) AS acceptance_rate,
              MAX(finished_at) FILTER (WHERE status = 'AC')            AS last_solved_at
            FROM submissions
            WHERE user_ref = :userRef
              AND is_test_run = false
            """, nativeQuery = true)
    List<Object[]> findUserStats(@Param("userRef") String userRef);

    @Query(value = """
            SELECT DISTINCT p.id, p.slug, p.title, MAX(s.finished_at) AS solved_at
            FROM submissions s
            JOIN problems p ON s.problem_id = p.id
            WHERE s.user_ref = :userRef
              AND s.status = 'AC'
              AND s.is_test_run = false
            GROUP BY p.id, p.slug, p.title
            ORDER BY solved_at DESC
            """, nativeQuery = true)
    List<Object[]> findSolvedProblems(@Param("userRef") String userRef);

    @Query(value = """
            SELECT language, COUNT(*) AS cnt
            FROM submissions
            WHERE user_ref = :userRef
              AND is_test_run = false
            GROUP BY language
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> findLanguageBreakdown(@Param("userRef") String userRef);

    @Query("SELECT s FROM Submission s WHERE s.userRef = :userRef AND s.isTestRun = false ORDER BY s.createdAt DESC")
    List<Submission> findRecentByUserRef(@Param("userRef") String userRef,
                                          org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT s FROM Submission s
            WHERE s.isTestRun = false
              AND (:problemSlug IS NULL OR s.problem.slug = :problemSlug)
              AND (:userRef     IS NULL OR s.userRef     = :userRef)
              AND (:status      IS NULL OR s.status      = :status)
            ORDER BY s.createdAt DESC
            """)
    org.springframework.data.domain.Page<Submission> findByFilters(
            @Param("problemSlug") String problemSlug,
            @Param("userRef")     String userRef,
            @Param("status")      String status,
            org.springframework.data.domain.Pageable pageable);
}
