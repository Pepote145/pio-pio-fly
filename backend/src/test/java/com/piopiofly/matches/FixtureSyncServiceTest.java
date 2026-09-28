package com.piopiofly.matches;

import com.piopiofly.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.piopiofly.support.Fixtures.awayMatch;
import static com.piopiofly.support.Fixtures.homeMatch;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class FixtureSyncServiceTest {

    private static final LocalDate OVIEDO_DATE = LocalDate.of(2026, 11, 1);

    @Autowired
    FixtureSyncService fixtureSyncService;

    @Autowired
    AwayMatchRepository awayMatchRepository;

    @Autowired
    StubFixtureProvider stubFixtureProvider;

    @BeforeEach
    void cleanDatabase() {
        awayMatchRepository.deleteAll();
    }

    @Test
    void storesOnlyAwayMatches() {
        stubFixtureProvider.fixtures = List.of(
                homeMatch("1", LocalDate.of(2026, 10, 4)),
                awayMatch("2", "real-oviedo", "Real Oviedo", OVIEDO_DATE, null));

        fixtureSyncService.sync();

        assertThat(awayMatchRepository.findAll())
                .extracting(AwayMatch::getHomeTeamSlug)
                .containsExactly("real-oviedo");
    }

    @Test
    void syncingTwiceDoesNotDuplicateMatches() {
        stubFixtureProvider.fixtures = List.of(awayMatch("2", "real-oviedo", "Real Oviedo", OVIEDO_DATE, null));

        FixtureSyncService.SyncResult first = fixtureSyncService.sync();
        FixtureSyncService.SyncResult second = fixtureSyncService.sync();

        assertThat(first).isEqualTo(new FixtureSyncService.SyncResult(1, 0));
        assertThat(second).isEqualTo(new FixtureSyncService.SyncResult(0, 1));
        assertThat(awayMatchRepository.count()).isEqualTo(1);
    }

    @Test
    void confirmsProvisionalMatchWhenLaligaFixesTheKickoff() {
        stubFixtureProvider.fixtures = List.of(awayMatch("2", "real-oviedo", "Real Oviedo", OVIEDO_DATE, null));
        fixtureSyncService.sync();

        Instant kickoff = Instant.parse("2026-10-31T17:00:00Z");
        stubFixtureProvider.fixtures = List.of(
                awayMatch("2", "real-oviedo", "Real Oviedo", LocalDate.of(2026, 10, 31), kickoff));
        fixtureSyncService.sync();

        AwayMatch match = awayMatchRepository.findAll().getFirst();
        assertThat(match.dateStatus()).isEqualTo(DateStatus.CONFIRMED);
        assertThat(match.getKickoffAt()).isEqualTo(kickoff);
        assertThat(match.getMatchDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    static class StubFixtureProvider implements FixtureProvider {

        List<Fixture> fixtures = new ArrayList<>();

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public List<Fixture> fetchFixtures() {
            return fixtures;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfig {

        @Bean
        @Primary
        StubFixtureProvider stubFixtureProvider() {
            return new StubFixtureProvider();
        }
    }
}
