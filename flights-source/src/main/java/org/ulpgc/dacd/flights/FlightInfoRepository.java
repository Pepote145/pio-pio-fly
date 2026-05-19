package org.ulpgc.dacd.flights;

import org.ulpgc.dacd.domain.DatabaseConfig;
import org.ulpgc.dacd.domain.FlightInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FlightInfoRepository {
    private static final String UPSERT_FLIGHT_INFO = """
            INSERT INTO flight_infos (
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
            ON CONFLICT (
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

    private final String databaseUrl;

    public FlightInfoRepository() {
        this(DatabaseConfig.DATABASE_URL);
    }

    public FlightInfoRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public void save(FlightInfo flightInfo) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(UPSERT_FLIGHT_INFO)) {
            statement.setString(1, flightInfo.getFlightNumber());
            statement.setString(2, flightInfo.getAirline());
            statement.setString(3, flightInfo.getOriginAirport());
            statement.setString(4, flightInfo.getDestinationAirport());
            statement.setString(5, flightInfo.getScheduledDateTime());
            statement.setString(6, flightInfo.getStatus());
            statement.setString(7, flightInfo.getTerminal());
            statement.setString(8, flightInfo.getSource());
            statement.setString(9, flightInfo.getCapturedAt() != null ? flightInfo.getCapturedAt().toString() : null);
            statement.executeUpdate();
        }
    }
}
