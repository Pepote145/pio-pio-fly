package com.piopiofly.matches.laliga;

import com.piopiofly.config.PioPioFlyProperties;
import com.piopiofly.matches.Fixture;
import com.piopiofly.matches.Fixture.TeamRef;
import com.piopiofly.matches.FixtureProvider;
import com.piopiofly.matches.FixtureProviderException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee el calendario del club desde laliga.com.
 *
 * <p>La página es una aplicación Next.js que incrusta sus datos en el script {@code __NEXT_DATA__}.
 * Leer ese JSON es mucho más estable que recorrer la tabla HTML.
 */
@Component
public class LaligaFixtureProvider implements FixtureProvider {

    static final String NAME = "laliga";

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final ZoneId DEFAULT_VENUE_ZONE = ZoneId.of("Europe/Madrid");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public LaligaFixtureProvider(PioPioFlyProperties properties, JsonMapper jsonMapper) {
        PioPioFlyProperties.Fixtures config = properties.fixtures();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.timeout());

        this.restClient = RestClient.builder()
                .baseUrl(config.laligaUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept-Language", "es-ES,es;q=0.9")
                .build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Fixture> fetchFixtures() {
        String html;
        try {
            html = restClient.get().retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new FixtureProviderException("No se pudo descargar el calendario de laliga.com", e);
        }
        if (html == null || html.isBlank()) {
            throw new FixtureProviderException("laliga.com ha devuelto una página vacía");
        }
        return parse(html);
    }

    List<Fixture> parse(String html) {
        Element script = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            throw new FixtureProviderException("La página de laliga.com ya no incluye __NEXT_DATA__: ha cambiado su formato");
        }

        JsonNode matches;
        try {
            matches = jsonMapper.readTree(script.data()).path("props").path("pageProps").path("matches");
        } catch (JacksonException e) {
            throw new FixtureProviderException("El JSON de laliga.com no se puede leer", e);
        }
        if (!matches.isArray() || matches.isEmpty()) {
            throw new FixtureProviderException("laliga.com no ha devuelto partidos: revisa si ha cambiado su formato");
        }

        List<Fixture> fixtures = new ArrayList<>();
        for (JsonNode match : matches) {
            fixtures.add(toFixture(match));
        }
        return fixtures;
    }

    private Fixture toFixture(JsonNode match) {
        JsonNode venue = match.path("venue");
        Instant kickoffAt = parseInstant(text(match, "time"));
        LocalDate matchDate = kickoffAt != null
                ? kickoffAt.atZone(venueZone(venue)).toLocalDate()
                : parseDate(text(match, "date"));

        return new Fixture(
                required(match, "id"),
                required(match.path("competition"), "name"),
                seasonName(match.path("season")),
                match.path("gameweek").path("week").isNumber() ? match.path("gameweek").path("week").asInt() : null,
                team(match.path("home_team")),
                team(match.path("away_team")),
                matchDate,
                kickoffAt,
                text(venue, "name"),
                text(venue, "city"),
                parseDouble(text(venue, "latitude")),
                parseDouble(text(venue, "longitude"))
        );
    }

    private TeamRef team(JsonNode team) {
        String name = text(team, "nickname");
        return new TeamRef(required(team, "slug"), name != null ? name : required(team, "name"));
    }

    /** {@code year = 2026} → {@code "2026/27"}. */
    private String seasonName(JsonNode season) {
        if (!season.path("year").isNumber()) {
            return null;
        }
        int year = season.path("year").asInt();
        return "%d/%02d".formatted(year, (year + 1) % 100);
    }

    private ZoneId venueZone(JsonNode venue) {
        String timezone = text(venue, "timezone");
        if (timezone == null) {
            return DEFAULT_VENUE_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return DEFAULT_VENUE_ZONE;
        }
    }

    private Instant parseInstant(String value) {
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }

    /** Fecha de referencia de la jornada: LaLiga la publica a las 00:00 del día, sin hora real. */
    private LocalDate parseDate(String value) {
        if (value == null) {
            throw new FixtureProviderException("Partido de laliga.com sin fecha");
        }
        return OffsetDateTime.parse(value).toLocalDate();
    }

    private Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new FixtureProviderException("Partido de laliga.com sin el campo obligatorio '" + field + "'");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }
}
