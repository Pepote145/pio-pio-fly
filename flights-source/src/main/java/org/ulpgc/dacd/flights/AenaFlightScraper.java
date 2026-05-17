package org.ulpgc.dacd.flights;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.ulpgc.dacd.domain.FlightInfo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;

public class AenaFlightScraper implements FlightInfoScraper {
    private static final String BASE_URL =
            "https://www.aena.es/sites/Satellite?pagename=AENA_ConsultarVuelos";
    private static final String FALLBACK_URL = "https://www.aena.es/es/infovuelos.html";
    private static final String REFERRER = "https://www.aena.es/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final String SOURCE_NAME = "aena.es";
    private static final int TIMEOUT_MILLIS = 20000;
    private static final Pattern FLIGHT_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z0-9]{2,3}\\s?\\d{2,4})\\b");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\s+(\\d{1,2}:\\d{2})\\b");
    private static final Pattern TERMINAL_PATTERN =
            Pattern.compile("\\b(?:terminal|t)\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> STATUS_KEYWORDS = List.of(
            "programado",
            "confirmado",
            "embarque",
            "en hora",
            "retrasado",
            "cancelado",
            "salida",
            "llegada",
            "scheduled",
            "on time",
            "boarding",
            "delayed",
            "cancelled"
    );

    @Override
    public List<FlightInfo> fetchFlights(String originAirport, String destinationAirport, String date) {
        try {
            Document document = fetchDocument(originAirport, destinationAirport, date);
            if (document == null) {
                System.out.println("No se han podido extraer vuelos de AENA con JSoup. Es posible que la web cargue los datos dinámicamente.");
                return List.of();
            }

            List<FlightInfo> flights = extractFlights(document, originAirport, destinationAirport, date);
            if (flights.isEmpty()) {
                System.out.println("No se han podido extraer vuelos de AENA con JSoup. Es posible que la web cargue los datos dinámicamente.");
            }
            return flights;
        } catch (HttpStatusException e) {
            printRequestDiagnostic(e.getUrl(), originAirport, destinationAirport, date,
                    "HTTP " + e.getStatusCode());
            System.out.println("AENA devolvio HTTP " + e.getStatusCode()
                    + " al consultar vuelos entre " + originAirport + " y " + destinationAirport + ".");
            return List.of();
        } catch (IOException e) {
            if (isSecureConnectionError(e)) {
                System.out.println("No se pudo establecer conexión segura con AENA desde Java. "
                        + "Revisa certificados/JDK o prueba inspeccionar la petición real desde el navegador.");
                return List.of();
            }

            System.out.println("No se pudo acceder a AENA para consultar vuelos: " + summarizeError(e));
            return List.of();
        }
    }

    private Document fetchDocument(String originAirport, String destinationAirport, String date) throws IOException {
        Document baseDocument = null;
        Document fallbackDocument = null;
        HttpStatusException lastHttpStatusException = null;
        IOException lastIOException = null;

        try {
            baseDocument = requestDocument(BASE_URL, originAirport, destinationAirport, date);
            if (containsFlightLikeContent(baseDocument)) {
                return baseDocument;
            }
        } catch (HttpStatusException e) {
            lastHttpStatusException = e;
        } catch (IOException e) {
            lastIOException = e;
            printRequestDiagnostic(BASE_URL, originAirport, destinationAirport, date, summarizeError(e));
        }

        try {
            fallbackDocument = requestDocument(FALLBACK_URL, originAirport, destinationAirport, date);
            if (containsFlightLikeContent(fallbackDocument)) {
                return fallbackDocument;
            }
        } catch (HttpStatusException e) {
            lastHttpStatusException = e;
        } catch (IOException e) {
            lastIOException = e;
            printRequestDiagnostic(FALLBACK_URL, originAirport, destinationAirport, date, summarizeError(e));
        }

        if (lastHttpStatusException != null) {
            throw lastHttpStatusException;
        }
        if (lastIOException != null) {
            throw lastIOException;
        }

        return fallbackDocument != null ? fallbackDocument : baseDocument;
    }

    private Document requestDocument(String url, String originAirport, String destinationAirport,
                                     String date) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(REFERRER)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .data("accion", "busqueda")
                .data("ordenacion", "Vuelo");

        if (originAirport != null && !originAirport.isBlank()) {
            connection.data("originBusqueda", originAirport);
        }
        if (destinationAirport != null && !destinationAirport.isBlank()) {
            connection.data("destinationBusqueda", destinationAirport);
        }
        if (date != null && !date.isBlank()) {
            connection.data("fechaBusqueda", date);
            connection.data("fecha", date);
        }

        Connection.Response response = connection.execute();
        if (response.statusCode() >= 400) {
            throw new HttpStatusException(
                    "AENA devolvio HTTP " + response.statusCode(),
                    response.statusCode(),
                    response.url().toString()
            );
        }

        String contentType = response.contentType();
        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
            return null;
        }
        return Jsoup.parse(body, response.url().toString());
    }

    private boolean containsFlightLikeContent(Document document) {
        if (document == null) {
            return false;
        }

        String text = normalizeSpaces(document.text()).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }

        return text.contains("vuelo")
                || text.contains("vuelos")
                || text.contains("flight")
                || text.contains("terminal")
                || FLIGHT_NUMBER_PATTERN.matcher(document.text()).find();
    }

    private List<FlightInfo> extractFlights(Document document, String originAirport,
                                            String destinationAirport, String date) {
        Set<String> candidateTexts = collectCandidateTexts(document);
        List<FlightInfo> flights = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        LocalDateTime capturedAt = LocalDateTime.now();

        for (String candidateText : candidateTexts) {
            FlightInfo flightInfo = createFlightInfo(candidateText, originAirport, destinationAirport, date, capturedAt);
            if (flightInfo == null) {
                continue;
            }

            String uniqueKey = buildUniqueKey(flightInfo);
            if (seenKeys.add(uniqueKey)) {
                flights.add(flightInfo);
            }
        }

        return flights;
    }

    private Set<String> collectCandidateTexts(Document document) {
        Set<String> candidateTexts = new LinkedHashSet<>();
        collectTexts(candidateTexts, document.select("table tr, tbody tr, li, article, [class*=flight], [class*=vuelo]"));
        return candidateTexts;
    }

    private void collectTexts(Set<String> candidateTexts, Elements elements) {
        for (Element element : elements) {
            String text = normalizeSpaces(element.text());
            if (looksLikeFlightText(text)) {
                candidateTexts.add(text);
            }
        }
    }

    private boolean looksLikeFlightText(String text) {
        if (text == null || text.isBlank() || text.length() > 500) {
            return false;
        }

        boolean hasTime = TIME_PATTERN.matcher(text).find();
        boolean hasFlightNumber = FLIGHT_NUMBER_PATTERN.matcher(text).find();
        String normalizedText = text.toLowerCase(Locale.ROOT);
        boolean hasKeyword = normalizedText.contains("vuelo")
                || normalizedText.contains("flight")
                || normalizedText.contains("terminal")
                || normalizedText.contains("salida")
                || normalizedText.contains("llegada");

        return hasTime && (hasFlightNumber || hasKeyword);
    }

    private FlightInfo createFlightInfo(String text, String originAirport, String destinationAirport,
                                        String date, LocalDateTime capturedAt) {
        String flightNumber = extractFirstMatch(text, FLIGHT_NUMBER_PATTERN);
        String scheduledDateTime = extractScheduledDateTime(text, date);
        String status = extractStatus(text);
        String terminal = extractTerminal(text);
        String airline = extractAirline(text, flightNumber, originAirport, destinationAirport, status, terminal);

        boolean mentionsRoute = containsAirportCode(text, originAirport) || containsAirportCode(text, destinationAirport);
        if (flightNumber == null && (scheduledDateTime == null || !mentionsRoute)) {
            return null;
        }

        return new FlightInfo(
                flightNumber,
                airline,
                originAirport,
                destinationAirport,
                scheduledDateTime,
                status,
                terminal,
                SOURCE_NAME,
                capturedAt
        );
    }

    private String extractScheduledDateTime(String text, String date) {
        Matcher dateTimeMatcher = DATE_TIME_PATTERN.matcher(text);
        if (dateTimeMatcher.find()) {
            String normalizedDate = dateTimeMatcher.group(1).replace('/', '-');
            return normalizedDate + "T" + normalizeTime(dateTimeMatcher.group(2));
        }

        String time = extractFirstMatch(text, TIME_PATTERN);
        if (time == null) {
            return null;
        }

        if (date == null || date.isBlank()) {
            return normalizeTime(time);
        }
        return date + "T" + normalizeTime(time);
    }

    private String extractStatus(String text) {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String keyword : STATUS_KEYWORDS) {
            if (normalizedText.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private String extractTerminal(String text) {
        String terminalNumber = extractFirstMatch(text, TERMINAL_PATTERN);
        if (terminalNumber == null) {
            return null;
        }
        return "T" + terminalNumber;
    }

    private String extractAirline(String text, String flightNumber, String originAirport,
                                  String destinationAirport, String status, String terminal) {
        String airline = normalizeSpaces(text);
        if (flightNumber != null) {
            airline = airline.replace(flightNumber, "");
        }
        if (originAirport != null) {
            airline = airline.replace(originAirport, "");
        }
        if (destinationAirport != null) {
            airline = airline.replace(destinationAirport, "");
        }
        if (status != null) {
            airline = airline.replace(status, "");
        }
        if (terminal != null) {
            airline = airline.replace(terminal, "");
        }

        airline = airline.replaceAll("\\d{1,2}:\\d{2}", "")
                .replaceAll("\\b\\d{2}[/-]\\d{2}[/-]\\d{4}\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (airline.isBlank() || airline.length() > 120) {
            return null;
        }
        return airline;
    }

    private String extractFirstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String normalizeTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        if (time.length() == 4) {
            return "0" + time;
        }
        return time;
    }

    private boolean containsAirportCode(String text, String airportCode) {
        if (text == null || airportCode == null || airportCode.isBlank()) {
            return false;
        }
        return text.toUpperCase(Locale.ROOT).contains(airportCode.toUpperCase(Locale.ROOT));
    }

    private String normalizeSpaces(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String buildUniqueKey(FlightInfo flightInfo) {
        return String.valueOf(flightInfo.getFlightNumber()) + '|'
                + String.valueOf(flightInfo.getScheduledDateTime()) + '|'
                + String.valueOf(flightInfo.getOriginAirport()) + '|'
                + String.valueOf(flightInfo.getDestinationAirport());
    }

    private boolean isSecureConnectionError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SSLHandshakeException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("pkix")
                        || normalizedMessage.contains("certificate_unknown")
                        || normalizedMessage.contains("unable to find valid certification path")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private String summarizeError(Throwable throwable) {
        Throwable current = throwable;
        String message = null;

        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return normalizeSpaces(message);
    }

    private void printRequestDiagnostic(String url, String originAirport, String destinationAirport,
                                        String date, String errorSummary) {
        System.out.println("Diagnostico AENA -> URL: " + url
                + " | origen: " + originAirport
                + " | destino: " + destinationAirport
                + " | fecha: " + date
                + " | error: " + errorSummary);
    }
}
