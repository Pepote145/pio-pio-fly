package com.piopiofly.matches.laliga;

import com.piopiofly.config.PioPioFlyProperties;
import com.piopiofly.matches.Fixture;
import com.piopiofly.matches.Fixture.TeamRef;
import com.piopiofly.matches.FixtureProviderException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaligaFixtureProviderTest {

    private final LaligaFixtureProvider provider = new LaligaFixtureProvider(
            new PioPioFlyProperties("ud-las-palmas", new PioPioFlyProperties.Fixtures(
                    URI.create("https://laliga.example"), Duration.ofSeconds(5), "-", false)),
            JsonMapper.builder().build());

    @Test
    void readsEveryFixtureFromNextData() {
        List<Fixture> fixtures = provider.parse(page("laliga/next-matches.html"));

        assertThat(fixtures)
                .extracting(fixture -> fixture.homeTeam().slug())
                .containsExactly("ud-las-palmas", "rcd-mallorca", "real-oviedo", "cd-tenerife");
    }

    @Test
    void readsConfirmedAwayMatch() {
        Fixture mallorca = fixtureAgainst("rcd-mallorca");

        assertThat(mallorca.externalId()).isEqualTo("102724");
        assertThat(mallorca.competition()).isEqualTo("LALIGA HYPERMOTION");
        assertThat(mallorca.season()).isEqualTo("2026/27");
        assertThat(mallorca.gameweek()).isEqualTo(9);
        assertThat(mallorca.homeTeam()).isEqualTo(new TeamRef("rcd-mallorca", "RCD Mallorca"));
        assertThat(mallorca.kickoffAt()).isEqualTo(Instant.parse("2026-10-11T16:30:00Z"));
        assertThat(mallorca.matchDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(mallorca.stadium()).isEqualTo("Estadi Mallorca Son Moix");
        assertThat(mallorca.city()).isEqualTo("Palma");
        assertThat(mallorca.latitude()).isNotNull();
        assertThat(mallorca.isAwayMatchFor("ud-las-palmas")).isTrue();
    }

    @Test
    void provisionalMatchHasGameweekDateButNoKickoff() {
        Fixture oviedo = fixtureAgainst("real-oviedo");

        assertThat(oviedo.kickoffAt()).isNull();
        assertThat(oviedo.matchDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void homeMatchIsNotAnAwayMatch() {
        assertThat(fixtureAgainst("ud-las-palmas").isAwayMatchFor("ud-las-palmas")).isFalse();
    }

    @Test
    void failsClearlyWhenPageFormatChanges() {
        assertThatThrownBy(() -> provider.parse("<html><body><table></table></body></html>"))
                .isInstanceOf(FixtureProviderException.class)
                .hasMessageContaining("__NEXT_DATA__");
    }

    @Test
    void failsClearlyWhenThereAreNoMatches() {
        String html = """
                <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"matches":[]}}}</script>
                """;

        assertThatThrownBy(() -> provider.parse(html))
                .isInstanceOf(FixtureProviderException.class)
                .hasMessageContaining("no ha devuelto partidos");
    }

    private Fixture fixtureAgainst(String homeSlug) {
        return provider.parse(page("laliga/next-matches.html")).stream()
                .filter(fixture -> fixture.homeTeam().slug().equals(homeSlug))
                .findFirst()
                .orElseThrow();
    }

    private static String page(String resource) {
        try (InputStream input = LaligaFixtureProviderTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
