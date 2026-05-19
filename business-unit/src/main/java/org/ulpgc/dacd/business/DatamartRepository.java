package org.ulpgc.dacd.business;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
}
