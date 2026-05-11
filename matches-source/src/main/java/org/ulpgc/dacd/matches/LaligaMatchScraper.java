package org.ulpgc.dacd.matches;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.ulpgc.dacd.domain.Match;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaligaMatchScraper implements MatchClient {
    private static final String LALIGA_URL =
            "https://www.laliga.com/en-GB/clubs/ud-las-palmas/next-matches";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final String DEFAULT_COMPETITION = "LALIGA HYPERMOTION";
    private static final String SOURCE_NAME = "laliga.com";
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{2}:\\d{2})");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    @Override
    public List<Match> fetchMatches() {
        try {
            Document document = Jsoup.connect(LALIGA_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .get();

            List<Match> matches = extractMatches(document);
            if (matches.isEmpty()) {
                throw new IllegalStateException(
                        "No se pudieron extraer partidos de laliga.com. Revisa el HTML de la pagina."
                );
            }
            return matches;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo acceder a laliga.com: " + e.getMessage(), e);
        }
    }

    private List<Match> extractMatches(Document document) {
        List<Match> matches = new ArrayList<>();
        Elements rows = document.select("table tbody tr:not(.row-more-info)");
        LocalDateTime capturedAt = LocalDateTime.now();

        for (Element row : rows) {
            Element dateCell = row.selectFirst("td[type=date]");
            Element timeCell = row.selectFirst("td[type=time]");
            if (dateCell == null || timeCell == null) {
                continue;
            }

            Elements cells = row.children();
            if (cells.size() < 4) {
                continue;
            }

            Elements teams = cells.get(2).select("a[href*='/clubs/'] p");
            if (teams.size() < 2) {
                continue;
            }

            String homeTeam = teams.get(0).text().trim();
            String awayTeam = teams.get(1).text().trim();
            LocalDateTime matchDate = parseMatchDate(dateCell.text(), timeCell.text());
            String competition = extractCompetition(cells.get(3).text());

            matches.add(new Match(
                    generateExternalId(homeTeam, awayTeam, matchDate),
                    competition,
                    homeTeam,
                    awayTeam,
                    matchDate,
                    null,
                    null,
                    null,
                    SOURCE_NAME,
                    capturedAt
            ));
        }

        return matches;
    }

    private String extractCompetition(String competitionText) {
        if (competitionText == null || competitionText.isBlank()) {
            return DEFAULT_COMPETITION;
        }
        return competitionText.trim();
    }

    private LocalDateTime parseMatchDate(String dateText, String timeText) {
        String normalizedDate = extractPattern(dateText, DATE_PATTERN);
        if (normalizedDate == null) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(normalizedDate, DATE_FORMATTER);
            String normalizedTime = extractPattern(timeText, TIME_PATTERN);
            if (normalizedTime == null) {
                return date.atStartOfDay();
            }

            LocalTime time = LocalTime.parse(normalizedTime, TIME_FORMATTER);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String extractPattern(String text, Pattern pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String generateExternalId(String homeTeam, String awayTeam, LocalDateTime matchDate) {
        String normalizedHomeTeam = normalizeIdentifierPart(homeTeam);
        String normalizedAwayTeam = normalizeIdentifierPart(awayTeam);
        String normalizedDate = matchDate != null
                ? matchDate.toString().replace(":", "-")
                : "unknown-date";

        return normalizedHomeTeam + "-" + normalizedAwayTeam + "-" + normalizedDate;
    }

    private String normalizeIdentifierPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-team";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }
}
