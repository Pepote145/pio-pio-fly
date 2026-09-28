package com.piopiofly.matches;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Partido fuera de casa tal y como lo ve el frontend.
 *
 * @param kickoffAt hora de inicio en UTC, o {@code null} si la fecha aún es provisional
 * @param airports  aeropuertos de destino, del recomendado al menos recomendado
 */
public record MatchResponse(
        long id,
        String competition,
        String season,
        Integer gameweek,
        String homeTeam,
        String homeTeamSlug,
        LocalDate matchDate,
        Instant kickoffAt,
        DateStatus dateStatus,
        String stadium,
        String city,
        List<Airport> airports
) {

    public record Airport(String iata, String note) {
    }
}
