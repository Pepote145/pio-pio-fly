package com.piopiofly;

import com.piopiofly.airports.TeamAirport;
import com.piopiofly.airports.TeamAirportRepository;
import com.piopiofly.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PioPioFlyApplicationTests {

    @Autowired
    TeamAirportRepository teamAirportRepository;

    /** Arranca la app entera: las migraciones Flyway y las entidades JPA tienen que cuadrar. */
    @Test
    void everyRivalOfTheSeasonHasARecommendedAirport() {
        assertThat(teamAirportRepository.findAll())
                .filteredOn(airport -> airport.getPriority() == 1)
                .extracting(TeamAirport::getTeamSlug)
                .doesNotHaveDuplicates()
                .hasSize(21);
    }
}
