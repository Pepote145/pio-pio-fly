package com.piopiofly.matches;

import com.piopiofly.config.ClockConfig;
import com.piopiofly.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static com.piopiofly.support.Fixtures.awayMatch;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@AutoConfigureMockMvc
class MatchControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AwayMatchRepository awayMatchRepository;

    @BeforeEach
    void seedMatches() {
        awayMatchRepository.deleteAll();
        save(awayMatch("past", "rc-deportivo", "RC Deportivo", LocalDate.of(2026, 5, 31), null));
        save(awayMatch("oviedo", "real-oviedo", "Real Oviedo", LocalDate.of(2026, 11, 1), null));
        save(awayMatch("mallorca", "rcd-mallorca", "RCD Mallorca", LocalDate.of(2026, 10, 11),
                Instant.parse("2026-10-11T16:30:00Z")));
    }

    @Test
    void listsUpcomingAwayMatchesInDateOrderWithTheirAirports() {
        var response = assertThat(mvc.get().uri("/api/matches")).hasStatusOk().bodyJson();

        response.extractingPath("$.length()").isEqualTo(2);
        response.extractingPath("$[0].homeTeam").isEqualTo("RCD Mallorca");
        response.extractingPath("$[0].dateStatus").isEqualTo("CONFIRMED");
        response.extractingPath("$[0].kickoffAt").isEqualTo("2026-10-11T16:30:00Z");
        response.extractingPath("$[0].airports[0].iata").isEqualTo("PMI");
        response.extractingPath("$[1].homeTeam").isEqualTo("Real Oviedo");
        response.extractingPath("$[1].dateStatus").isEqualTo("PROVISIONAL");
        response.extractingPath("$[1].kickoffAt").isNull();
        response.extractingPath("$[1].airports[0].iata").isEqualTo("OVD");
    }

    private void save(Fixture fixture) {
        AwayMatch match = new AwayMatch("test", fixture.externalId());
        match.updateFrom(fixture);
        awayMatchRepository.save(match);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ClockConfig.CANARY_ZONE);
        }
    }
}
