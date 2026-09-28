package com.piopiofly.matches;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
class MatchController {

    private final MatchQueryService matchQueryService;

    MatchController(MatchQueryService matchQueryService) {
        this.matchQueryService = matchQueryService;
    }

    @GetMapping
    List<MatchResponse> upcomingAwayMatches() {
        return matchQueryService.upcomingAwayMatches();
    }
}
