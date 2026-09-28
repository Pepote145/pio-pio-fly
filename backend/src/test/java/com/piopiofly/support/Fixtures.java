package com.piopiofly.support;

import com.piopiofly.matches.Fixture;
import com.piopiofly.matches.Fixture.TeamRef;

import java.time.Instant;
import java.time.LocalDate;

/** Partidos de ejemplo para los tests. */
public final class Fixtures {

    public static final String CLUB_SLUG = "ud-las-palmas";
    public static final TeamRef LAS_PALMAS = new TeamRef(CLUB_SLUG, "UD Las Palmas");

    private Fixtures() {
    }

    public static Fixture awayMatch(String externalId, String homeSlug, String homeName, LocalDate date,
                                    Instant kickoffAt) {
        return new Fixture(externalId, "LALIGA HYPERMOTION", "2026/27", 9,
                new TeamRef(homeSlug, homeName), LAS_PALMAS, date, kickoffAt,
                "Estadio de " + homeName, "Ciudad de " + homeName, 40.0, -3.0);
    }

    public static Fixture homeMatch(String externalId, LocalDate date) {
        return new Fixture(externalId, "LALIGA HYPERMOTION", "2026/27", 8,
                LAS_PALMAS, new TeamRef("r-valladolid-cf", "Real Valladolid CF"), date, null,
                "Estadio Gran Canaria", "Las Palmas de Gran Canaria", 28.07, -15.44);
    }
}
