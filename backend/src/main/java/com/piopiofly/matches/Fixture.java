package com.piopiofly.matches;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Partido tal y como lo publica una fuente de calendario, antes de guardarlo.
 *
 * @param matchDate día del partido; si el horario no está confirmado, el día de referencia de la jornada
 * @param kickoffAt hora de inicio, o {@code null} mientras la fecha sea provisional
 */
public record Fixture(
        String externalId,
        String competition,
        String season,
        Integer gameweek,
        TeamRef homeTeam,
        TeamRef awayTeam,
        LocalDate matchDate,
        Instant kickoffAt,
        String stadium,
        String city,
        Double latitude,
        Double longitude
) {

    public boolean isAwayMatchFor(String clubSlug) {
        return awayTeam.slug().equals(clubSlug);
    }

    public record TeamRef(String slug, String name) {
    }
}
