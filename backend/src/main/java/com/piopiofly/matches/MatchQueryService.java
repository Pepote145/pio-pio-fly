package com.piopiofly.matches;

import com.piopiofly.airports.TeamAirport;
import com.piopiofly.airports.TeamAirportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatchQueryService {

    private final AwayMatchRepository awayMatchRepository;
    private final TeamAirportRepository teamAirportRepository;
    private final Clock clock;

    public MatchQueryService(AwayMatchRepository awayMatchRepository,
                             TeamAirportRepository teamAirportRepository, Clock clock) {
        this.awayMatchRepository = awayMatchRepository;
        this.teamAirportRepository = teamAirportRepository;
        this.clock = clock;
    }

    /** Partidos fuera de casa de hoy en adelante, por orden de fecha. */
    @Transactional(readOnly = true)
    public List<MatchResponse> upcomingAwayMatches() {
        List<AwayMatch> matches = awayMatchRepository
                .findByMatchDateGreaterThanEqualOrderByMatchDateAsc(LocalDate.now(clock));

        List<String> homeTeams = matches.stream().map(AwayMatch::getHomeTeamSlug).distinct().toList();
        Map<String, List<MatchResponse.Airport>> airportsByTeam = teamAirportRepository
                .findByTeamSlugInOrderByPriorityAsc(homeTeams).stream()
                .collect(Collectors.groupingBy(
                        TeamAirport::getTeamSlug,
                        Collectors.mapping(
                                airport -> new MatchResponse.Airport(airport.getIata(), airport.getNote()),
                                Collectors.toList())));

        return matches.stream()
                .map(match -> toResponse(match, airportsByTeam.getOrDefault(match.getHomeTeamSlug(), List.of())))
                .toList();
    }

    private MatchResponse toResponse(AwayMatch match, List<MatchResponse.Airport> airports) {
        return new MatchResponse(
                match.getId(),
                match.getCompetition(),
                match.getSeason(),
                match.getGameweek(),
                match.getHomeTeamName(),
                match.getHomeTeamSlug(),
                match.getMatchDate(),
                match.getKickoffAt(),
                match.dateStatus(),
                match.getStadium(),
                match.getCity(),
                airports
        );
    }
}
