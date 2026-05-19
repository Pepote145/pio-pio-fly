package org.ulpgc.dacd.matches;

import org.ulpgc.dacd.domain.AirportMapping;
import org.ulpgc.dacd.domain.EventMessage;
import org.ulpgc.dacd.domain.EventPublisher;
import org.ulpgc.dacd.domain.EventTopics;
import org.ulpgc.dacd.domain.Match;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AwayMatchService {
    private static final String TARGET_TEAM = "UD Las Palmas";
    private static final String SOURCE_ID = "laliga-matches-source";

    private final MatchClient matchClient;
    private final AirportMapping airportMapping;
    private final AwayMatchRepository repository;
    private final EventPublisher eventPublisher;

    public AwayMatchService(MatchClient matchClient, AirportMapping airportMapping,
                            AwayMatchRepository repository) {
        this(matchClient, airportMapping, repository, null);
    }

    public AwayMatchService(MatchClient matchClient, AirportMapping airportMapping,
                            AwayMatchRepository repository, EventPublisher eventPublisher) {
        this.matchClient = matchClient;
        this.airportMapping = airportMapping;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
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
                publishAwayMatchEvent(awayMatch);
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

    private void publishAwayMatchEvent(Match match) {
        if (eventPublisher == null) {
            return;
        }

        try {
            eventPublisher.publish(EventTopics.AWAY_MATCH, EventMessage.capturedNow(SOURCE_ID, buildPayload(match)));
        } catch (IllegalStateException e) {
            System.out.println("No se pudo publicar evento AwayMatch en ActiveMQ: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(Match match) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalId", match.getExternalId());
        payload.put("competition", match.getCompetition());
        payload.put("homeTeam", match.getHomeTeam());
        payload.put("awayTeam", match.getAwayTeam());
        payload.put("matchDate", formatDateTime(match.getMatchDate()));
        payload.put("city", match.getCity());
        payload.put("stadium", match.getStadium());
        payload.put("destinationAirport", match.getDestinationAirport());
        payload.put("source", match.getSource());
        payload.put("capturedAt", formatDateTime(match.getCapturedAt()));
        return payload;
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.toString() : null;
    }
}
