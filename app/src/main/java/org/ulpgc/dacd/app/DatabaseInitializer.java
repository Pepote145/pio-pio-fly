package org.ulpgc.dacd.app;

import org.ulpgc.dacd.domain.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    public static final String DATABASE_NAME = DatabaseConfig.DATABASE_NAME;
    private static final String DATABASE_URL = DatabaseConfig.DATABASE_URL;

    private static final String CREATE_AWAY_MATCHES_TABLE = """
            CREATE TABLE IF NOT EXISTS away_matches (
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

    private static final String CREATE_FLIGHT_OFFERS_TABLE = """
            CREATE TABLE IF NOT EXISTS flight_offers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                match_external_id TEXT,
                origin_airport TEXT,
                destination_airport TEXT,
                departure_date TEXT,
                return_date TEXT,
                airline TEXT,
                price_original REAL,
                currency TEXT,
                resident_discount_applicable INTEGER,
                estimated_resident_price REAL,
                source TEXT,
                captured_at TEXT
            )
            """;

    private static final String CREATE_FLIGHT_INFOS_TABLE = """
            CREATE TABLE IF NOT EXISTS flight_infos (
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

    private static final String CREATE_FLIGHT_INFOS_UNIQUE_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_flight_infos_unique_flight
            ON flight_infos (
                flight_number,
                origin_airport,
                destination_airport,
                scheduled_datetime,
                source
            )
            """;

    public void initializeDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_AWAY_MATCHES_TABLE);
            statement.execute(CREATE_FLIGHT_OFFERS_TABLE);
            statement.execute(CREATE_FLIGHT_INFOS_TABLE);
            statement.execute(CREATE_FLIGHT_INFOS_UNIQUE_INDEX);
        }
    }
}
