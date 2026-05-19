package org.ulpgc.dacd.business;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatamartRepository {
    private static final String COUNT_AWAY_MATCHES = "SELECT COUNT(*) FROM away_matches_datamart";
    private static final String COUNT_FLIGHTS = "SELECT COUNT(*) FROM flight_infos_datamart";
    private static final String COUNT_DESTINATIONS = """
            SELECT COUNT(DISTINCT destination_airport)
            FROM (
                SELECT destination_airport FROM away_matches_datamart
                UNION ALL
                SELECT destination_airport FROM flight_infos_datamart
            )
            WHERE destination_airport IS NOT NULL AND TRIM(destination_airport) <> ''
            """;
    private static final String COUNT_SOURCES = """
            SELECT COUNT(DISTINCT source)
            FROM (
                SELECT source FROM away_matches_datamart
                UNION ALL
                SELECT source FROM flight_infos_datamart
            )
            WHERE source IS NOT NULL AND TRIM(source) <> ''
            """;
    private static final String UPSERT_AWAY_MATCH = """
            INSERT INTO away_matches_datamart (
                external_id,
                competition,
                home_team,
                away_team,
                match_date,
                city,
                stadium,
                destination_airport,
                source,
                captured_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(external_id, source) DO UPDATE SET
                competition = excluded.competition,
                home_team = excluded.home_team,
                away_team = excluded.away_team,
                match_date = excluded.match_date,
                city = excluded.city,
                stadium = excluded.stadium,
                destination_airport = excluded.destination_airport,
                captured_at = excluded.captured_at
            """;
    private static final String UPSERT_FLIGHT_INFO = """
            INSERT INTO flight_infos_datamart (
                flight_number,
                airline,
                origin_airport,
                destination_airport,
                scheduled_datetime,
                status,
                terminal,
                source,
                captured_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(
                flight_number,
                origin_airport,
                destination_airport,
                scheduled_datetime,
                source
            ) DO UPDATE SET
                airline = excluded.airline,
                status = excluded.status,
                terminal = excluded.terminal,
                captured_at = excluded.captured_at
            """;
    private static final String SELECT_UPCOMING_AWAY_MATCHES = """
            SELECT
                external_id,
                competition,
                home_team,
                away_team,
                match_date,
                city,
                stadium,
                destination_airport,
                source
            FROM away_matches_datamart
            WHERE match_date IS NOT NULL AND TRIM(match_date) <> ''
            ORDER BY match_date ASC
            """;
    private static final String SELECT_FLIGHTS_BY_DESTINATION = """
            SELECT
                flight_number,
                airline,
                origin_airport,
                destination_airport,
                scheduled_datetime,
                status,
                terminal,
                source
            FROM flight_infos_datamart
            WHERE UPPER(destination_airport) = UPPER(?)
            ORDER BY scheduled_datetime ASC
            """;
    private static final String SELECT_AVAILABLE_DESTINATIONS = """
            SELECT DISTINCT destination_airport
            FROM flight_infos_datamart
            WHERE destination_airport IS NOT NULL AND TRIM(destination_airport) <> ''
            ORDER BY destination_airport ASC
            """;

    public DatamartSummary getSummary() throws SQLException {
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             Statement statement = connection.createStatement()) {
            return new DatamartSummary(
                    queryCount(statement, COUNT_AWAY_MATCHES),
                    queryCount(statement, COUNT_FLIGHTS),
                    queryCount(statement, COUNT_DESTINATIONS),
                    queryCount(statement, COUNT_SOURCES)
            );
        }
    }

    private int queryCount(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public void saveAwayMatchFromEvent(String externalId, String competition, String homeTeam, String awayTeam,
                                       String matchDate, String city, String stadium, String destinationAirport,
                                       String source, String capturedAt) throws SQLException {
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(UPSERT_AWAY_MATCH)) {
            statement.setString(1, externalId);
            statement.setString(2, competition);
            statement.setString(3, homeTeam);
            statement.setString(4, awayTeam);
            statement.setString(5, matchDate);
            statement.setString(6, city);
            statement.setString(7, stadium);
            statement.setString(8, destinationAirport);
            statement.setString(9, source);
            statement.setString(10, capturedAt);
            statement.executeUpdate();
        }
    }

    public void saveFlightInfoFromEvent(String flightNumber, String airline, String originAirport,
                                        String destinationAirport, String scheduledDateTime, String status,
                                        String terminal, String source, String capturedAt) throws SQLException {
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(UPSERT_FLIGHT_INFO)) {
            statement.setString(1, flightNumber);
            statement.setString(2, airline);
            statement.setString(3, originAirport);
            statement.setString(4, destinationAirport);
            statement.setString(5, scheduledDateTime);
            statement.setString(6, status);
            statement.setString(7, terminal);
            statement.setString(8, source);
            statement.setString(9, capturedAt);
            statement.executeUpdate();
        }
    }

    public List<AwayMatchView> findUpcomingAwayMatches() throws SQLException {
        List<AwayMatchView> matches = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(SELECT_UPCOMING_AWAY_MATCHES);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                matches.add(toAwayMatchView(resultSet));
            }
        }
        return matches;
    }

    public List<FlightInfoView> findFlightsByDestination(String destinationAirport) throws SQLException {
        List<FlightInfoView> flights = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(SELECT_FLIGHTS_BY_DESTINATION)) {
            statement.setString(1, destinationAirport);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    flights.add(toFlightInfoView(resultSet));
                }
            }
        }
        return flights;
    }

    public List<String> findAvailableDestinations() throws SQLException {
        List<String> destinations = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(SELECT_AVAILABLE_DESTINATIONS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                destinations.add(resultSet.getString("destination_airport"));
            }
        }
        return destinations;
    }

    public List<TravelRecommendation> buildTravelRecommendations() throws SQLException {
        List<TravelRecommendation> recommendations = new ArrayList<>();
        for (AwayMatchView match : findUpcomingAwayMatches()) {
            if (isBlank(match.destinationAirport())) {
                recommendations.add(new TravelRecommendation(
                        match,
                        match.destinationAirport(),
                        0,
                        null,
                        "Recomendacion: no hay aeropuerto destino registrado para este desplazamiento."
                ));
                continue;
            }

            List<FlightInfoView> flights = findFlightsByDestination(match.destinationAirport());
            FlightInfoView firstFlight = flights.isEmpty() ? null : flights.getFirst();
            recommendations.add(new TravelRecommendation(
                    match,
                    match.destinationAirport(),
                    flights.size(),
                    firstFlight,
                    "Recomendacion: revisar vuelos hacia " + match.destinationAirport()
                            + " para el desplazamiento a " + displayValue(match.city()) + "."
            ));
        }
        return recommendations;
    }

    private AwayMatchView toAwayMatchView(ResultSet resultSet) throws SQLException {
        return new AwayMatchView(
                resultSet.getString("external_id"),
                resultSet.getString("competition"),
                resultSet.getString("home_team"),
                resultSet.getString("away_team"),
                resultSet.getString("match_date"),
                resultSet.getString("city"),
                resultSet.getString("stadium"),
                resultSet.getString("destination_airport"),
                resultSet.getString("source")
        );
    }

    private FlightInfoView toFlightInfoView(ResultSet resultSet) throws SQLException {
        return new FlightInfoView(
                resultSet.getString("flight_number"),
                resultSet.getString("airline"),
                resultSet.getString("origin_airport"),
                resultSet.getString("destination_airport"),
                resultSet.getString("scheduled_datetime"),
                resultSet.getString("status"),
                resultSet.getString("terminal"),
                resultSet.getString("source")
        );
    }

    private String displayValue(String value) {
        return isBlank(value) ? "destino sin ciudad registrada" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
