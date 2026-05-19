package org.ulpgc.dacd.flights;

import org.ulpgc.dacd.domain.DatabaseConfig;
import org.ulpgc.dacd.domain.EventMessage;
import org.ulpgc.dacd.domain.EventPublisher;
import org.ulpgc.dacd.domain.EventTopics;
import org.ulpgc.dacd.domain.FlightInfo;
import org.ulpgc.dacd.domain.Match;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlightInfoService {
    private static final String ORIGIN_AIRPORT = "LPA";
    private static final String MATCH_SOURCE = "laliga.com";
    private static final String SOURCE_ID = "aena-flights-source";
    private static final String SELECT_AWAY_MATCHES = """
            SELECT
                external_id,
                competition,
                home_team,
                away_team,
                match_date,
                city,
                stadium,
                destination_airport,
                source,
                MAX(captured_at) AS captured_at
            FROM away_matches
            WHERE source = ?
            GROUP BY
                external_id,
                competition,
                home_team,
                away_team,
                match_date,
                city,
                stadium,
                destination_airport,
                source
            ORDER BY match_date
            """;

    private final FlightInfoScraper flightInfoScraper;
    private final FlightInfoRepository flightInfoRepository;
    private final String databaseUrl;
    private final EventPublisher eventPublisher;

    public FlightInfoService(FlightInfoScraper flightInfoScraper, FlightInfoRepository flightInfoRepository) {
        this(flightInfoScraper, flightInfoRepository, DatabaseConfig.DATABASE_URL, null);
    }

    public FlightInfoService(FlightInfoScraper flightInfoScraper, FlightInfoRepository flightInfoRepository,
                             EventPublisher eventPublisher) {
        this(flightInfoScraper, flightInfoRepository, DatabaseConfig.DATABASE_URL, eventPublisher);
    }

    public FlightInfoService(FlightInfoScraper flightInfoScraper, FlightInfoRepository flightInfoRepository,
                             String databaseUrl) {
        this(flightInfoScraper, flightInfoRepository, databaseUrl, null);
    }

    public FlightInfoService(FlightInfoScraper flightInfoScraper, FlightInfoRepository flightInfoRepository,
                             String databaseUrl, EventPublisher eventPublisher) {
        this.flightInfoScraper = flightInfoScraper;
        this.flightInfoRepository = flightInfoRepository;
        this.databaseUrl = databaseUrl;
        this.eventPublisher = eventPublisher;
    }

    public int captureFlightsForAwayMatches() throws SQLException {
        List<Match> awayMatches = loadAwayMatches();
        int insertedFlights = 0;

        for (Match awayMatch : awayMatches) {
            if (awayMatch.getDestinationAirport() == null || awayMatch.getDestinationAirport().isBlank()) {
                System.out.println("Se omite el partido " + describeMatch(awayMatch)
                        + " porque no tiene destination_airport.");
                continue;
            }

            String date = extractMatchDate(awayMatch);
            System.out.println("Consultando vuelos AENA: " + ORIGIN_AIRPORT
                    + " -> " + awayMatch.getDestinationAirport()
                    + " para " + describeMatch(awayMatch)
                    + " en fecha " + date + ".");
            List<FlightInfo> flights = flightInfoScraper.fetchFlights(
                    ORIGIN_AIRPORT,
                    awayMatch.getDestinationAirport(),
                    date
            );

            if (flights.isEmpty()) {
                System.out.println("AENA no ha devuelto vuelos para el partido " + describeMatch(awayMatch) + ".");
                continue;
            }

            int savedFlightsForMatch = 0;
            for (FlightInfo flightInfo : flights) {
                if (hasMissingUniqueKey(flightInfo)) {
                    System.out.println("Se omite un vuelo de AENA sin clave completa para evitar duplicados.");
                    continue;
                }

                flightInfoRepository.save(flightInfo);
                publishFlightInfoEvent(flightInfo);
                insertedFlights++;
                savedFlightsForMatch++;
            }

            System.out.println("Se han guardado o actualizado " + savedFlightsForMatch
                    + " vuelos de AENA para el partido " + describeMatch(awayMatch) + ".");
        }

        return insertedFlights;
    }

    private List<Match> loadAwayMatches() throws SQLException {
        List<Match> awayMatches = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(SELECT_AWAY_MATCHES)) {
            statement.setString(1, MATCH_SOURCE);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    awayMatches.add(new Match(
                            resultSet.getString("external_id"),
                            resultSet.getString("competition"),
                            resultSet.getString("home_team"),
                            resultSet.getString("away_team"),
                            parseDateTime(resultSet.getString("match_date")),
                            resultSet.getString("city"),
                            resultSet.getString("stadium"),
                            resultSet.getString("destination_airport"),
                            resultSet.getString("source"),
                            parseDateTime(resultSet.getString("captured_at"))
                    ));
                }
            }
        }

        return awayMatches;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String extractMatchDate(Match match) {
        if (match.getMatchDate() == null) {
            return null;
        }
        LocalDate date = match.getMatchDate().toLocalDate();
        return date.toString();
    }

    private String describeMatch(Match match) {
        return match.getHomeTeam() + " vs " + match.getAwayTeam();
    }

    private boolean hasMissingUniqueKey(FlightInfo flightInfo) {
        return isBlank(flightInfo.getFlightNumber())
                || isBlank(flightInfo.getOriginAirport())
                || isBlank(flightInfo.getDestinationAirport())
                || isBlank(flightInfo.getScheduledDateTime())
                || isBlank(flightInfo.getSource());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void publishFlightInfoEvent(FlightInfo flightInfo) {
        if (eventPublisher == null) {
            return;
        }

        try {
            eventPublisher.publish(EventTopics.FLIGHT_INFO, EventMessage.capturedNow(SOURCE_ID, buildPayload(flightInfo)));
        } catch (IllegalStateException e) {
            System.out.println("No se pudo publicar evento FlightInfo en ActiveMQ: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(FlightInfo flightInfo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flightNumber", flightInfo.getFlightNumber());
        payload.put("airline", flightInfo.getAirline());
        payload.put("originAirport", flightInfo.getOriginAirport());
        payload.put("destinationAirport", flightInfo.getDestinationAirport());
        payload.put("scheduledDateTime", flightInfo.getScheduledDateTime());
        payload.put("status", flightInfo.getStatus());
        payload.put("terminal", flightInfo.getTerminal());
        payload.put("source", flightInfo.getSource());
        payload.put("capturedAt", formatDateTime(flightInfo.getCapturedAt()));
        return payload;
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.toString() : null;
    }
}
