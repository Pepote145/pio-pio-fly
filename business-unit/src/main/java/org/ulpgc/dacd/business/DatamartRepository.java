package org.ulpgc.dacd.business;

import java.sql.Connection;
import java.sql.DriverManager;
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
}
