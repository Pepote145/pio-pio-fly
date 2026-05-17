package org.ulpgc.dacd.matches;

import org.ulpgc.dacd.domain.AirportMapping;
import org.ulpgc.dacd.domain.Match;

import java.sql.SQLException;
import java.util.Locale;
import java.util.List;

public class AwayMatchService {
    private static final String TARGET_TEAM = "UD Las Palmas";

    private final MatchClient matchClient;
    private final AirportMapping airportMapping;
    private final AwayMatchRepository repository;

    public AwayMatchService(MatchClient matchClient, AirportMapping airportMapping,
                            AwayMatchRepository repository) {
        this.matchClient = matchClient;
        this.airportMapping = airportMapping;
        this.repository = repository;
    }

    public int captureAwayMatches() throws SQLException {
        List<Match> matches = matchClient.fetchMatches();
        int insertedMatches = 0;

        for (Match match : matches) {
            if (isAwayMatchForUdLasPalmas(match)) {
                Match awayMatch = new Match(
                        match.getExternalId(),
                        match.getCompetition(),
                        match.getHomeTeam(),
                        match.getAwayTeam(),
                        match.getMatchDate(),
                        match.getCity(),
                        match.getStadium(),
                        resolveDestinationAirport(match),
                        match.getSource(),
                        match.getCapturedAt()
                );
                repository.save(awayMatch);
                insertedMatches++;
            }
        }

        System.out.println("Se han guardado " + insertedMatches + " partidos fuera de casa desde laliga.com.");
        return insertedMatches;
    }

    private boolean isAwayMatchForUdLasPalmas(Match match) {
        String awayTeam = match.getAwayTeam();
        if (awayTeam == null || awayTeam.isBlank()) {
            return false;
        }

        String normalizedAwayTeam = awayTeam.toLowerCase(Locale.ROOT);
        return normalizedAwayTeam.contains("las palmas") || TARGET_TEAM.equalsIgnoreCase(awayTeam);
    }

    private String resolveDestinationAirport(Match match) {
        String airportCode = airportMapping.getAirportCode(match.getHomeTeam());
        if (airportCode == null) {
            System.out.println("No se encontro aeropuerto para el equipo local: " + match.getHomeTeam());
        }
        return airportCode;
    }
}
