package com.judge.service;

import com.judge.api.dto.*;
import com.judge.domain.*;
import com.judge.exception.JudgeException;
import com.judge.repository.*;
import com.judge.security.ApiKeyContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;

    public ContestService(ContestRepository contestRepository,
                          ContestProblemRepository contestProblemRepository,
                          ContestParticipantRepository contestParticipantRepository,
                          ProblemRepository problemRepository,
                          SubmissionRepository submissionRepository) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
    }

    // ── Admin ──────────────────────────────────────────────────────────────────

    @Transactional
    public ContestResponse create(ContestRequest req) {
        if (contestRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Slug already exists: " + req.getSlug());
        validateTimes(req);
        Contest contest = Contest.builder()
                .slug(req.getSlug())
                .title(req.getTitle())
                .description(req.getDescription())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .isPublic(req.isPublic())
                .createdBy(ApiKeyContext.get())
                .build();
        contest = contestRepository.save(contest);
        return ContestResponse.from(contest, List.of());
    }

    @Transactional
    public ContestResponse update(Long id, ContestRequest req) {
        Contest contest = getOrThrow(id);
        if (!contest.getSlug().equals(req.getSlug()) && contestRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Slug already exists: " + req.getSlug());
        validateTimes(req);
        contest.setSlug(req.getSlug());
        contest.setTitle(req.getTitle());
        contest.setDescription(req.getDescription());
        contest.setStartTime(req.getStartTime());
        contest.setEndTime(req.getEndTime());
        contest.setPublic(req.isPublic());
        List<ContestProblem> problems = contestProblemRepository.findByContestIdOrderByOrderIndexAsc(id);
        return ContestResponse.from(contestRepository.save(contest), problems);
    }

    @Transactional
    public ContestProblemResponse addProblem(Long contestId, ContestProblemRequest req) {
        Contest contest = getOrThrow(contestId);
        Problem problem = problemRepository.findById(req.getProblemId())
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + req.getProblemId()));
        if (contestProblemRepository.existsByContestIdAndProblemId(contestId, req.getProblemId()))
            throw JudgeException.badRequest("Problem already in contest");
        int order = req.getOrderIndex() > 0 ? req.getOrderIndex()
                : contestProblemRepository.findByContestIdOrderByOrderIndexAsc(contestId).size();
        ContestProblem cp = ContestProblem.builder()
                .contest(contest)
                .problem(problem)
                .orderIndex(order)
                .alias(req.getAlias())
                .build();
        return ContestProblemResponse.from(contestProblemRepository.save(cp));
    }

    @Transactional
    public void removeProblem(Long contestId, Long problemId) {
        getOrThrow(contestId);
        ContestProblem cp = contestProblemRepository.findByContestIdAndProblemId(contestId, problemId)
                .orElseThrow(() -> JudgeException.notFound("Problem not in contest"));
        contestProblemRepository.delete(cp);
    }

    @Transactional(readOnly = true)
    public List<ScoreboardEntry> getAdminScoreboard(Long contestId) {
        Contest contest = getOrThrow(contestId);
        return buildScoreboard(contest);
    }

    // ── Public ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContestResponse> listActive() {
        return contestRepository
                .findByIsPublicTrueAndEndTimeAfterOrderByStartTimeAsc(LocalDateTime.now())
                .stream()
                .map(c -> ContestResponse.from(c,
                        contestProblemRepository.findByContestIdOrderByOrderIndexAsc(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContestResponse getBySlug(String slug) {
        Contest contest = contestRepository.findBySlug(slug)
                .orElseThrow(() -> JudgeException.notFound("Contest not found: " + slug));
        if (!contest.isPublic())
            throw JudgeException.forbidden("Contest is not public");
        List<ContestProblem> problems = contestProblemRepository
                .findByContestIdOrderByOrderIndexAsc(contest.getId());
        return ContestResponse.from(contest, problems);
    }

    @Transactional
    public void register(String slug, String userRef) {
        if (userRef == null || userRef.isBlank())
            throw JudgeException.badRequest("userRef is required");
        Contest contest = contestRepository.findBySlug(slug)
                .orElseThrow(() -> JudgeException.notFound("Contest not found: " + slug));
        if (!contest.isPublic())
            throw JudgeException.forbidden("Contest is not public");
        if (LocalDateTime.now().isAfter(contest.getEndTime()))
            throw JudgeException.badRequest("Contest has ended");
        if (contestParticipantRepository.existsByContestIdAndUserRef(contest.getId(), userRef))
            throw JudgeException.badRequest("Already registered");
        contestParticipantRepository.save(ContestParticipant.builder()
                .contest(contest).userRef(userRef).build());
    }

    @Transactional(readOnly = true)
    public List<ScoreboardEntry> getPublicScoreboard(String slug) {
        Contest contest = contestRepository.findBySlug(slug)
                .orElseThrow(() -> JudgeException.notFound("Contest not found: " + slug));
        if (!contest.isPublic())
            throw JudgeException.forbidden("Contest is not public");
        return buildScoreboard(contest);
    }

    // ── Contest submission validation ──────────────────────────────────────────

    /**
     * Called by SubmissionService when contestId is present in SubmitRequest.
     * Returns the validated contest, throws if any check fails.
     */
    @Transactional(readOnly = true)
    public Contest validateContestSubmission(Long contestId, Long problemId, String userRef) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> JudgeException.notFound("Contest not found: " + contestId));
        if (!contest.isOngoing())
            throw JudgeException.badRequest("Contest is not currently running");
        if (!contestProblemRepository.existsByContestIdAndProblemId(contestId, problemId))
            throw JudgeException.badRequest("Problem is not in this contest");
        if (userRef == null || userRef.isBlank())
            throw JudgeException.badRequest("userRef is required for contest submissions");
        if (!contestParticipantRepository.existsByContestIdAndUserRef(contestId, userRef))
            throw JudgeException.badRequest("You are not registered for this contest");
        return contest;
    }

    // ── Scoreboard calculation ─────────────────────────────────────────────────

    private List<ScoreboardEntry> buildScoreboard(Contest contest) {
        List<ContestProblem> contestProblems =
                contestProblemRepository.findByContestIdOrderByOrderIndexAsc(contest.getId());
        Map<Long, ContestProblem> problemMap = contestProblems.stream()
                .collect(Collectors.toMap(cp -> cp.getProblem().getId(), cp -> cp));

        List<Submission> submissions = submissionRepository
                .findByContestIdAndIsTestRunFalse(contest.getId())
                .stream()
                .filter(s -> !Set.of("PENDING", "JUDGING").contains(s.getStatus()))
                .sorted(Comparator.comparing(Submission::getCreatedAt))
                .toList();

        // Group by userRef → problemId → ordered submissions
        Map<String, Map<Long, List<Submission>>> byUserProblem = new LinkedHashMap<>();
        for (Submission s : submissions) {
            byUserProblem
                    .computeIfAbsent(s.getUserRef(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(s.getProblem().getId(), k -> new ArrayList<>())
                    .add(s);
        }

        List<ScoreboardEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<Long, List<Submission>>> userEntry : byUserProblem.entrySet()) {
            String userRef = userEntry.getKey();
            int totalScore = 0;
            long totalPenalty = 0;
            List<ScoreboardEntry.ProblemScore> problemScores = new ArrayList<>();

            for (ContestProblem cp : contestProblems) {
                Long problemId = cp.getProblem().getId();
                List<Submission> subs = userEntry.getValue().getOrDefault(problemId, List.of());

                int wrongAttempts = 0;
                boolean solved = false;
                int score = 0;
                Long minutesSinceStart = null;

                for (Submission s : subs) {
                    if (solved) break;
                    if ("AC".equals(s.getStatus())) {
                        solved = true;
                        score = s.getScore();
                        minutesSinceStart = ChronoUnit.MINUTES.between(
                                contest.getStartTime(), s.getCreatedAt());
                    } else if ("WA".equals(s.getStatus()) || "TLE".equals(s.getStatus())
                            || "MLE".equals(s.getStatus()) || "RE".equals(s.getStatus())) {
                        wrongAttempts++;
                    }
                }

                if (solved) {
                    totalScore += score;
                    totalPenalty += minutesSinceStart + (long) wrongAttempts * 20;
                }

                problemScores.add(ScoreboardEntry.ProblemScore.builder()
                        .alias(cp.getAlias())
                        .problemId(problemId)
                        .score(score)
                        .wrongAttempts(wrongAttempts)
                        .minutesSinceStart(minutesSinceStart)
                        .solved(solved)
                        .build());
            }

            entries.add(ScoreboardEntry.builder()
                    .userRef(userRef)
                    .totalScore(totalScore)
                    .totalPenaltyMinutes(totalPenalty)
                    .problems(problemScores)
                    .build());
        }

        // Sort: totalScore DESC, totalPenalty ASC
        entries.sort(Comparator.comparingInt(ScoreboardEntry::getTotalScore).reversed()
                .thenComparingLong(ScoreboardEntry::getTotalPenaltyMinutes));

        // Assign ranks
        for (int i = 0; i < entries.size(); i++) entries.get(i).setRank(i + 1);
        return entries;
    }

    private Contest getOrThrow(Long id) {
        return contestRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("Contest not found: " + id));
    }

    private void validateTimes(ContestRequest req) {
        if (!req.getEndTime().isAfter(req.getStartTime()))
            throw JudgeException.badRequest("end_time must be after start_time");
    }
}
