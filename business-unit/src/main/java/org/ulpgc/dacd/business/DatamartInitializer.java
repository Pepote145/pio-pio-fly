package org.ulpgc.dacd.business;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatamartInitializer {
    private static final String CREATE_AWAY_MATCHES_DATAMART_TABLE = """
            CREATE TABLE IF NOT EXISTS away_matches_datamart (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                external_id TEXT,
                competition TEXT,
                home_team TEXT,
                away_team TEXT,
                match_date TEXT,
                city TEXT,
                stadium TEXT,
                destination_airport TEXT,
                source TEXT,
                captured_at TEXT
            )
            """;

    private static final String CREATE_FLIGHT_INFOS_DATAMART_TABLE = """
            CREATE TABLE IF NOT EXISTS flight_infos_datamart (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                flight_number TEXT,
                airline TEXT,
                origin_airport TEXT,
                destination_airport TEXT,
                scheduled_datetime TEXT,
                status TEXT,
                terminal TEXT,
                source TEXT,
                captured_at TEXT
            )
            """;

    private static final String CREATE_AWAY_MATCHES_UNIQUE_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_away_matches_datamart_unique
            ON away_matches_datamart (external_id, source)
            """;

    private static final String CREATE_FLIGHT_INFOS_UNIQUE_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_flight_infos_datamart_unique
            ON flight_infos_datamart (
                flight_number,
                origin_airport,
                destination_airport,
                scheduled_datetime,
                source
            )
            """;

    public void initialize() throws SQLException {
        try (Connection connection = DriverManager.getConnection(BusinessUnitConfig.DATAMART_DATABASE_URL);
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_AWAY_MATCHES_DATAMART_TABLE);
            statement.execute(CREATE_FLIGHT_INFOS_DATAMART_TABLE);
            statement.execute(CREATE_AWAY_MATCHES_UNIQUE_INDEX);
            statement.execute(CREATE_FLIGHT_INFOS_UNIQUE_INDEX);
        }
    }
}
