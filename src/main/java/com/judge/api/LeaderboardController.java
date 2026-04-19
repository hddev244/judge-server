package com.judge.api;

import com.judge.api.dto.LeaderboardEntry;
import com.judge.api.dto.UserStatsResponse;
import com.judge.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/api/v1/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        limit = Math.min(limit, 200);
        return ResponseEntity.ok(leaderboardService.getLeaderboard(limit, offset));
    }

    @GetMapping("/api/v1/users/{userRef}/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(@PathVariable String userRef) {
        return ResponseEntity.ok(leaderboardService.getUserStats(userRef));
    }
}
