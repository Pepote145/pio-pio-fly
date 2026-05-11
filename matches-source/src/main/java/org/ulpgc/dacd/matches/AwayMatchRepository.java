package org.ulpgc.dacd.matches;

import org.ulpgc.dacd.domain.DatabaseConfig;
import org.ulpgc.dacd.domain.Match;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AwayMatchRepository {
    private static final String INSERT_AWAY_MATCH = """
            INSERT INTO away_matches (
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
            """;

    private final String databaseUrl;

    public AwayMatchRepository() {
        this(DatabaseConfig.DATABASE_URL);
    }

    public AwayMatchRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public void save(Match match) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(INSERT_AWAY_MATCH)) {
            statement.setString(1, match.getExternalId());
            statement.setString(2, match.getCompetition());
            statement.setString(3, match.getHomeTeam());
            statement.setString(4, match.getAwayTeam());
            statement.setString(5, match.getMatchDate() != null ? match.getMatchDate().toString() : null);
            statement.setString(6, match.getCity());
            statement.setString(7, match.getStadium());
            statement.setString(8, match.getDestinationAirport());
            statement.setString(9, match.getSource());
            statement.setString(10, match.getCapturedAt() != null ? match.getCapturedAt().toString() : null);
            statement.executeUpdate();
        }
    }
}
