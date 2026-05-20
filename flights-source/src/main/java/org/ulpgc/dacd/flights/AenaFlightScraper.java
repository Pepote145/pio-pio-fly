package org.ulpgc.dacd.flights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.ulpgc.dacd.domain.FlightInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;

public class AenaFlightScraper implements FlightInfoScraper {
    private static final String BASE_URL =
            "https://www.aena.es/sites/Satellite?pagename=AENA_ConsultarVuelos";
    private static final String REFERRER = "https://www.aena.es/es/infovuelos.html";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String SOURCE_NAME = "aena.es";
    private static final String GRAN_CANARIA_AIRPORT = "LPA";
    private static final String A_CORUNA_AIRPORT = "LCG";
    private static final int TIMEOUT_MILLIS = 20000;
    private static final int CURL_TIMEOUT_SECONDS = 30;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Pattern FLIGHT_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z0-9]{2,3}\\s?\\d{2,4})\\b");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\s+(\\d{1,2}:\\d{2})\\b");
    private static final Pattern TERMINAL_PATTERN =
            Pattern.compile("\\b(?:terminal|t)\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter AENA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter AENA_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
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
        System.out.println("AENA consulta solicitada: origen=" + display(originAirport)
                + ", destino=" + display(destinationAirport)
                + ", fecha=" + display(date) + ".");

        LocalDate requestedDate = parseDate(date);
        if (requestedDate == null) {
            System.out.println("AENA no puede consultar vuelos porque la fecha no es valida: " + display(date));
            return List.of();
        }

        List<FlightInfo> flights = scrapeFlights(originAirport, destinationAirport, requestedDate);
        if (flights.isEmpty()) {
            System.out.println("AENA no devolvio vuelos reales para esta ruta y fecha.");
        }
        return flights;
    }

    public List<FlightInfo> scrapeFlights(String originAirport, String destinationAirport, LocalDate date) {
        if (date == null) {
            System.out.println("AENA no puede consultar vuelos porque la fecha es nula.");
            return List.of();
        }

        try {
            return fetchEndpointFlights(originAirport, destinationAirport, date);
        } catch (HttpStatusException e) {
            printRequestDiagnostic(e.getUrl(), originAirport, destinationAirport, date.toString(),
                    "HTTP " + e.getStatusCode());
            System.out.println("AENA devolvio HTTP " + e.getStatusCode()
                    + " al consultar vuelos entre " + originAirport + " y " + destinationAirport + ".");
            return List.of();
        } catch (IOException e) {
            List<FlightInfo> localFlights = fetchLocalDevelopmentFlights(
                    originAirport,
                    destinationAirport,
                    date.toString()
            );
            if (!localFlights.isEmpty()) {
                return localFlights;
            }

            if (isSecureConnectionError(e)) {
                System.out.println("No se pudo establecer conexión segura con AENA desde Java. "
                        + "Revisa certificados/JDK o prueba inspeccionar la petición real desde el navegador.");
                return List.of();
            }

            System.out.println("No se pudo acceder a AENA para consultar vuelos: " + summarizeError(e));
            return List.of();
        }
    }

    private List<FlightInfo> fetchEndpointFlights(String originAirport, String destinationAirport,
                                                  LocalDate date) throws IOException {
        String normalizedOrigin = normalizeAirportCode(originAirport);
        String normalizedDestination = normalizeAirportCode(destinationAirport);
        if (normalizedOrigin == null || normalizedDestination == null) {
            System.out.println("AENA no puede consultar vuelos sin origen y destino.");
            return List.of();
        }

        System.out.println("Consultando AENA " + normalizedOrigin + " -> " + normalizedDestination);
        System.out.println("Fecha: " + date);

        List<FlightInfo> departureAirportFlights = fetchFlightsFromAirport(normalizedOrigin, "S");
        List<FlightInfo> departureMatches = filterAndDeduplicateFlights(
                departureAirportFlights,
                normalizedOrigin,
                normalizedDestination,
                date
        );
        System.out.println("AENA vuelos parseados antes de filtrar desde airport="
                + normalizedOrigin + ", flightType=S: " + departureAirportFlights.size());
        System.out.println("AENA vuelos filtrados " + normalizedOrigin + " -> "
                + normalizedDestination + " fecha " + date + ": " + departureMatches.size());

        if (!departureMatches.isEmpty()) {
            System.out.println("AENA consulta de llegadas omitida porque salidas ya devolvio vuelos reales.");
            return departureMatches;
        }

        List<FlightInfo> arrivalAirportFlights = fetchFlightsFromAirport(normalizedDestination, "L");
        List<FlightInfo> arrivalMatches = filterAndDeduplicateFlights(
                arrivalAirportFlights,
                normalizedOrigin,
                normalizedDestination,
                date
        );
        System.out.println("AENA vuelos parseados antes de filtrar desde airport="
                + normalizedDestination + ", flightType=L: " + arrivalAirportFlights.size());
        System.out.println("AENA vuelos filtrados " + normalizedOrigin + " -> "
                + normalizedDestination + " fecha " + date + ": " + arrivalMatches.size());
        return arrivalMatches;
    }

    private List<FlightInfo> fetchFlightsFromAirport(String airport, String flightType) throws IOException {
        String url = buildInfovuelosUrl(airport, flightType);
        System.out.println("Consultando AENA:");
        System.out.println("airport=" + airport);
        System.out.println("flightType=" + flightType);
        System.out.println("url=" + url);

        String body = fetchAenaEndpointBody(url);
        if (body == null || body.isBlank()) {
            System.out.println("AENA respuesta vacia para airport=" + airport + ", flightType=" + flightType + ".");
            return List.of();
        }

        System.out.println("Tipo de respuesta: " + responseType(body, "application/json"));
        List<FlightInfo> flights = parseAenaJsonFlights(body, flightType);
        System.out.println("Vuelos crudos encontrados: " + flights.size());
        return flights;
    }

    private String fetchAenaEndpointBody(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MILLIS))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERRER)
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "es-ES,es;q=0.9")
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP status=" + response.statusCode());
            System.out.println("response length=" + response.body().length());
            if (response.statusCode() >= 400) {
                throw new HttpStatusException(
                        "AENA devolvio HTTP " + response.statusCode(),
                        response.statusCode(),
                        url
                );
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta AENA interrumpida", e);
        } catch (IOException e) {
            if (!isSecureConnectionError(e)) {
                throw e;
            }

            System.out.println("AENA TLS Java fallo; se intenta consulta remota real con curl del sistema.");
            return fetchAenaEndpointBodyWithSystemCurl(url, e);
        }
    }

    private String fetchAenaEndpointBodyWithSystemCurl(String url, IOException originalException) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "curl",
                "-sS",
                "-L",
                "-X", "POST",
                "-d", "",
                "-A", USER_AGENT,
                "-H", "Referer: " + REFERRER,
                "-H", "Accept: application/json,text/plain,*/*",
                "-H", "Accept-Language: es-ES,es;q=0.9",
                "-w", "\\n__AENA_HTTP_STATUS__:%{http_code}",
                url
        );

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = readProcessStream(process.getInputStream());
            CompletableFuture<String> stderrFuture = readProcessStream(process.getErrorStream());
            boolean finished = process.waitFor(CURL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.out.println("AENA curl timeout tras " + CURL_TIMEOUT_SECONDS + " segundos.");
                throw originalException;
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();
            if (process.exitValue() != 0) {
                System.out.println("AENA curl fallo: " + normalizeSpaces(stderr));
                throw originalException;
            }

            CurlResponse curlResponse = splitCurlResponse(stdout);
            System.out.println("HTTP status=" + curlResponse.statusCode());
            System.out.println("response length=" + curlResponse.body().length());
            if (curlResponse.statusCode() >= 400) {
                throw new HttpStatusException(
                        "AENA devolvio HTTP " + curlResponse.statusCode(),
                        curlResponse.statusCode(),
                        url
                );
            }
            return curlResponse.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw originalException;
        } catch (ExecutionException e) {
            throw originalException;
        }
    }

    private List<FlightInfo> parseAenaJsonFlights(String body, String flightType) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        if (!root.isArray()) {
            System.out.println("AENA JSON recibido pero no es un array de vuelos.");
            return List.of();
        }

        List<FlightInfo> flights = new ArrayList<>();
        LocalDateTime capturedAt = LocalDateTime.now();
        for (JsonNode node : root) {
            String aenaAirport = readJsonText(node, "iataAena");
            String otherAirport = readJsonText(node, "iataOtro");
            String originAirport;
            String destinationAirport;

            if ("L".equalsIgnoreCase(flightType)) {
                originAirport = otherAirport;
                destinationAirport = aenaAirport;
            } else {
                originAirport = aenaAirport;
                destinationAirport = otherAirport;
            }

            String scheduledDateTime = combineAenaScheduledDateTime(
                    readJsonText(node, "fecha"),
                    readJsonText(node, "horaProgramada")
            );
            if (scheduledDateTime == null) {
                System.out.println("AENA fecha/hora no parseable: fecha="
                        + display(readJsonText(node, "fecha"))
                        + ", horaProgramada=" + display(readJsonText(node, "horaProgramada")));
            }

            flights.add(new FlightInfo(
                    buildJsonFlightNumber(node),
                    readJsonText(node, "nombreCompania"),
                    normalizeAirportCode(originAirport),
                    normalizeAirportCode(destinationAirport),
                    scheduledDateTime,
                    readJsonText(node, "estado"),
                    readJsonText(node, "terminal"),
                    SOURCE_NAME,
                    capturedAt
            ));
        }
        return flights;
    }

    private List<FlightInfo> filterAndDeduplicateFlights(List<FlightInfo> flights, String originAirport,
                                                         String destinationAirport, LocalDate date) {
        List<FlightInfo> filteredFlights = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        Set<String> parsedDates = new LinkedHashSet<>();

        for (FlightInfo flight : flights) {
            LocalDate flightDate = parseDate(flight.getScheduledDateTime());
            if (flightDate != null) {
                parsedDates.add(flightDate.toString());
            }
            if (!matchesAirportCode(flight.getOriginAirport(), originAirport)
                    || !matchesAirportCode(flight.getDestinationAirport(), destinationAirport)
                    || flightDate == null
                    || !flightDate.equals(date)) {
                continue;
            }

            if (seenKeys.add(buildUniqueKey(flight))) {
                filteredFlights.add(flight);
                System.out.println("AENA vuelo parseado: "
                        + display(flight.getFlightNumber())
                        + " | " + display(flight.getAirline())
                        + " | " + display(flight.getOriginAirport())
                        + " -> " + display(flight.getDestinationAirport())
                        + " | " + display(flight.getScheduledDateTime()));
            }
        }

        System.out.println("Fechas de vuelos parseados por AENA: " + parsedDates);
        return filteredFlights;
    }

    private String combineAenaScheduledDateTime(String date, String time) {
        LocalDate parsedDate = parseDate(date);
        LocalTime parsedTime = parseTime(time);
        if (parsedDate == null || parsedTime == null) {
            return null;
        }
        return LocalDateTime.of(parsedDate, parsedTime).toString();
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_TIME,
                DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
                AENA_TIME_FORMATTER
        )) {
            try {
                return LocalTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String normalizeAirportCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<FlightInfo> fetchRemoteFlights(String originAirport, String destinationAirport,
                                                String date) throws IOException {
        if (isLpaToLcg(originAirport, destinationAirport)) {
            return scrapeLpaToLcg(date);
        }
        return requestAenaInfovuelos(originAirport, destinationAirport, date);
    }

    private boolean isLpaToLcg(String originAirport, String destinationAirport) {
        return matchesAirportCode(originAirport, GRAN_CANARIA_AIRPORT)
                && matchesAirportCode(destinationAirport, A_CORUNA_AIRPORT);
    }

    private List<FlightInfo> scrapeLpaToLcg(String date) throws IOException {
        String url = buildInfovuelosUrl(GRAN_CANARIA_AIRPORT, "S");
        System.out.println("Consultando AENA LPA -> LCG");
        System.out.println("Fecha: " + display(date));
        System.out.println("URL usada: " + url);
        System.out.println("Modo: remoto");

        List<FlightInfo> flights = requestFlights(
                url,
                GRAN_CANARIA_AIRPORT,
                A_CORUNA_AIRPORT,
                date,
                true
        );
        System.out.println("Vuelos filtrados LPA -> LCG para " + display(date) + ": " + flights.size());
        return flights;
    }

    private List<FlightInfo> requestAenaInfovuelos(String originAirport, String destinationAirport,
                                                   String date) throws IOException {
        AenaEndpointRequest departureRequest = new AenaEndpointRequest(originAirport, "S");
        List<FlightInfo> departureFlights = requestInfovuelosEndpoint(
                departureRequest,
                originAirport,
                destinationAirport,
                date
        );
        if (!departureFlights.isEmpty()) {
            System.out.println("AENA Infovuelos total filtrado para " + display(originAirport)
                    + " -> " + display(destinationAirport)
                    + " en fecha " + display(date)
                    + ": consulta salidas=" + departureFlights.size()
                    + ", consulta llegadas=omitida, vuelos finales=" + departureFlights.size() + ".");
            return departureFlights;
        }

        AenaEndpointRequest arrivalRequest = new AenaEndpointRequest(destinationAirport, "L");
        List<FlightInfo> arrivalFlights = requestInfovuelosEndpoint(
                arrivalRequest,
                originAirport,
                destinationAirport,
                date
        );

        System.out.println("AENA Infovuelos total filtrado para " + display(originAirport)
                + " -> " + display(destinationAirport)
                + " en fecha " + display(date)
                + ": consulta salidas=0"
                + ", consulta llegadas=" + arrivalFlights.size()
                + ", vuelos finales=" + arrivalFlights.size() + ".");
        return arrivalFlights;
    }

    private List<FlightInfo> requestInfovuelosEndpoint(AenaEndpointRequest request, String originAirport,
                                                       String destinationAirport, String date) throws IOException {
        if (request.airport() == null || request.airport().isBlank()) {
            return List.of();
        }

        String url = buildInfovuelosUrl(request.airport(), request.flightType());
        List<FlightInfo> requestedFlights = requestFlights(url, originAirport, destinationAirport, date, true);
        List<FlightInfo> flights = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (FlightInfo flight : requestedFlights) {
            if (seenKeys.add(buildUniqueKey(flight))) {
                flights.add(flight);
            }
        }
        return flights;
    }

    private String buildInfovuelosUrl(String airport, String flightType) {
        return BASE_URL
                + "&airport=" + airport.trim().toUpperCase(Locale.ROOT)
                + "&flightType=" + flightType
                + "&l=es_ES";
    }

    private List<FlightInfo> requestFlights(String url, String originAirport, String destinationAirport,
                                            String date, boolean filterByRequestedDate) throws IOException {
        System.out.println("AENA remoto: " + describeRequest(url, originAirport, destinationAirport, date));
        Connection connection = Jsoup.connect(url)
                .method(Connection.Method.POST)
                .userAgent(USER_AGENT)
                .referrer(REFERRER)
                .header("Accept", "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .requestBody("");

        Connection.Response response;
        try {
            response = connection.execute();
        } catch (IOException e) {
            if (isSecureConnectionError(e)) {
                System.out.println("AENA TLS Java fallo; se intenta consulta remota real con curl del sistema.");
                return requestFlightsWithSystemCurl(
                        url,
                        originAirport,
                        destinationAirport,
                        date,
                        filterByRequestedDate,
                        e
                );
            }
            throw e;
        }

        System.out.println("AENA respuesta remota: url=" + response.url()
                + ", http=" + response.statusCode()
                + ", contentType=" + display(response.contentType()) + ".");
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
            System.out.println("AENA remoto sin cuerpo de respuesta.");
            return List.of();
        }
        System.out.println("AENA tamaño respuesta: " + body.length()
                + " caracteres, tipo=" + responseType(body, contentType) + ".");
        return extractFlightsFromBody(
                body,
                contentType,
                response.url().toString(),
                originAirport,
                destinationAirport,
                date,
                filterByRequestedDate
        );
    }

    private List<FlightInfo> requestFlightsWithSystemCurl(String url, String originAirport,
                                                          String destinationAirport, String date,
                                                          boolean filterByRequestedDate,
                                                          IOException originalException) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "curl",
                "-sS",
                "-L",
                "-X", "POST",
                "-d", "",
                "-A", USER_AGENT,
                "-H", "Accept: application/json,text/html,*/*",
                "-H", "Accept-Language: es-ES,es;q=0.9",
                "-w", "\\n__AENA_HTTP_STATUS__:%{http_code}",
                url
        );

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = readProcessStream(process.getInputStream());
            CompletableFuture<String> stderrFuture = readProcessStream(process.getErrorStream());
            boolean finished = process.waitFor(CURL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw originalException;
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();
            if (process.exitValue() != 0) {
                System.out.println("AENA curl fallo: " + normalizeSpaces(stderr));
                throw originalException;
            }

            CurlResponse curlResponse = splitCurlResponse(stdout);
            System.out.println("AENA respuesta remota via curl: url=" + url
                    + ", http=" + curlResponse.statusCode()
                    + ", contentType=application/json/text.");
            if (curlResponse.statusCode() >= 400) {
                throw new HttpStatusException(
                        "AENA devolvio HTTP " + curlResponse.statusCode(),
                        curlResponse.statusCode(),
                        url
                );
            }
            if (curlResponse.body().isBlank()) {
                System.out.println("AENA curl sin cuerpo de respuesta.");
                return List.of();
            }
            System.out.println("AENA tamaño respuesta: " + curlResponse.body().length()
                    + " caracteres, tipo=" + responseType(curlResponse.body(), "application/json") + ".");

            return extractFlightsFromBody(
                    curlResponse.body(),
                    "application/json",
                    url,
                    originAirport,
                    destinationAirport,
                    date,
                    filterByRequestedDate
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw originalException;
        } catch (ExecutionException e) {
            throw originalException;
        }
    }

    private CompletableFuture<String> readProcessStream(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private CurlResponse splitCurlResponse(String stdout) {
        String marker = "\n__AENA_HTTP_STATUS__:";
        int markerIndex = stdout.lastIndexOf(marker);
        if (markerIndex < 0) {
            return new CurlResponse(stdout, 0);
        }

        String body = stdout.substring(0, markerIndex);
        String statusText = stdout.substring(markerIndex + marker.length()).trim();
        try {
            return new CurlResponse(body, Integer.parseInt(statusText));
        } catch (NumberFormatException e) {
            return new CurlResponse(body, 0);
        }
    }

    private List<FlightInfo> fetchLocalDevelopmentFlights(String originAirport, String destinationAirport, String date) {
        for (Path localFile : localFallbackFiles(originAirport, destinationAirport, date)) {
            List<FlightInfo> flights = readLocalDevelopmentFlights(localFile, originAirport, destinationAirport, date);
            if (!flights.isEmpty()) {
                return flights;
            }
        }
        return List.of();
    }

    private List<Path> localFallbackFiles(String originAirport, String destinationAirport, String date) {
        Set<Path> files = new LinkedHashSet<>();
        if (originAirport == null || originAirport.isBlank()
                || destinationAirport == null || destinationAirport.isBlank()
                || date == null || date.isBlank()) {
            return List.of();
        }

        String routeDate = date.trim();
        String upperRoute = originAirport.trim().toUpperCase(Locale.ROOT)
                + "_" + destinationAirport.trim().toUpperCase(Locale.ROOT)
                + "_" + routeDate;
        String lowerRoute = upperRoute.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".html", ".json")) {
            files.add(Path.of("aena_" + upperRoute + extension));
            files.add(Path.of("aena_" + lowerRoute + extension));
        }
        return new ArrayList<>(files);
    }

    private List<FlightInfo> readLocalDevelopmentFlights(Path localFile, String originAirport,
                                                         String destinationAirport, String date) {
        if (!Files.exists(localFile)) {
            return List.of();
        }
        System.out.println("AENA fallback local: revisando " + localFile
                + " para " + display(originAirport) + " -> " + display(destinationAirport)
                + " en fecha " + display(date) + ".");
        try {
            String body = Files.readString(localFile);
            List<FlightInfo> flights = extractFlightsFromBody(
                    body,
                    null,
                    localFile.toString(),
                    originAirport,
                    destinationAirport,
                    date,
                    true
            );
            if (!flights.isEmpty()) {
                System.out.println("Usando archivo local " + localFile
                        + " como fallback de desarrollo con datos reales de AENA para la fecha " + date + ".");
            } else {
                System.out.println("El archivo local " + localFile
                        + " no contiene datos reales para esta ruta y fecha.");
            }
            return flights;
        } catch (IOException e) {
            System.out.println("No se pudo leer el archivo local de AENA " + localFile + ": " + summarizeError(e));
            return List.of();
        }
    }

    private List<FlightInfo> extractFlightsFromBody(String body, String contentType, String sourceUrl,
                                                    String originAirport, String destinationAirport,
                                                    String date, boolean filterByRequestedDate) throws IOException {
        if (isJsonContent(body, contentType)) {
            return extractFlightsFromJson(body, originAirport, destinationAirport, date, filterByRequestedDate);
        }

        Document document = Jsoup.parse(body, sourceUrl);
        if (!containsFlightLikeContent(document)) {
            System.out.println("AENA HTML sin contenido reconocible de vuelos en " + sourceUrl + ".");
            return List.of();
        }
        return extractFlights(document, originAirport, destinationAirport, date, filterByRequestedDate);
    }

    private boolean isJsonContent(String body, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return true;
        }
        String trimmedBody = body.trim();
        return trimmedBody.startsWith("[") || trimmedBody.startsWith("{");
    }

    private List<FlightInfo> extractFlightsFromJson(String body, String originAirport,
                                                    String destinationAirport, String date,
                                                    boolean filterByRequestedDate) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        if (!root.isArray()) {
            System.out.println("AENA JSON recibido pero no es un array de vuelos.");
            return List.of();
        }

        List<FlightInfo> flights = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        LocalDateTime capturedAt = LocalDateTime.now();
        int rawFlights = root.size();
        int routeMatchedFlights = 0;
        int dateMatchedFlights = 0;
        Set<String> parsedDates = new LinkedHashSet<>();

        for (JsonNode node : root) {
            if (!matchesRoute(node, originAirport, destinationAirport)) {
                continue;
            }
            routeMatchedFlights++;

            String scheduledDateTime = extractJsonScheduledDateTime(node);
            parsedDates.add(displayDate(scheduledDateTime));
            if (filterByRequestedDate && !matchesRequestedDate(scheduledDateTime, date)) {
                continue;
            }
            dateMatchedFlights++;

            FlightInfo flightInfo = new FlightInfo(
                    buildJsonFlightNumber(node),
                    readJsonText(node, "nombreCompania"),
                    originAirport,
                    destinationAirport,
                    scheduledDateTime,
                    readJsonText(node, "estado"),
                    readJsonText(node, "terminal"),
                    SOURCE_NAME,
                    capturedAt
            );

            String uniqueKey = buildUniqueKey(flightInfo);
            if (seenKeys.add(uniqueKey)) {
                flights.add(flightInfo);
                System.out.println("AENA vuelo parseado: "
                        + display(flightInfo.getFlightNumber())
                        + " | " + display(flightInfo.getAirline())
                        + " | " + display(flightInfo.getOriginAirport())
                        + " -> " + display(flightInfo.getDestinationAirport())
                        + " | " + display(flightInfo.getScheduledDateTime()));
            }
        }

        System.out.println("AENA JSON vuelos crudos: " + rawFlights
                + ", coinciden con ruta: " + routeMatchedFlights
                + ", coinciden con fecha: " + dateMatchedFlights
                + ", parseados finales: " + flights.size()
                + ", fechas parseadas: " + parsedDates + ".");
        return flights;
    }

    private boolean matchesRoute(JsonNode node, String originAirport, String destinationAirport) {
        String aenaAirport = readJsonText(node, "iataAena");
        String otherAirport = readJsonText(node, "iataOtro");
        if (aenaAirport == null || otherAirport == null) {
            return false;
        }

        boolean arrivalAtDestination = destinationAirport.equalsIgnoreCase(aenaAirport)
                && originAirport.equalsIgnoreCase(otherAirport);
        boolean departureFromOrigin = originAirport.equalsIgnoreCase(aenaAirport)
                && destinationAirport.equalsIgnoreCase(otherAirport);

        return arrivalAtDestination || departureFromOrigin;
    }

    private boolean matchesAirportCode(String actual, String expected) {
        return actual != null && expected != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private String buildJsonFlightNumber(JsonNode node) {
        String flightNumber = readJsonText(node, "numVuelo");
        String airlineIata = readJsonText(node, "iataCompania");
        if (flightNumber == null) {
            return null;
        }
        if (airlineIata == null || flightNumber.toUpperCase(Locale.ROOT).startsWith(airlineIata)) {
            return flightNumber;
        }
        return airlineIata + flightNumber;
    }

    private String extractJsonScheduledDateTime(JsonNode node) {
        String date = readJsonText(node, "fecha");
        String time = readJsonText(node, "horaProgramada");
        if (date == null || time == null) {
            return null;
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date, AENA_DATE_FORMATTER);
            LocalTime parsedTime = LocalTime.parse(time, AENA_TIME_FORMATTER);
            return LocalDateTime.of(parsedDate, parsedTime).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String readJsonText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text.trim();
    }

    private boolean containsFlightLikeContent(Document document) {
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
                                            String destinationAirport, String date,
                                            boolean filterByRequestedDate) {
        Set<String> candidateTexts = collectCandidateTexts(document);
        List<FlightInfo> flights = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        LocalDateTime capturedAt = LocalDateTime.now();
        int rawCandidates = candidateTexts.size();
        int parsedCandidates = 0;
        int dateMatchedFlights = 0;
        Set<String> parsedDates = new LinkedHashSet<>();

        for (String candidateText : candidateTexts) {
            FlightInfo flightInfo = createFlightInfo(candidateText, originAirport, destinationAirport, date, capturedAt);
            if (flightInfo == null) {
                continue;
            }
            parsedCandidates++;
            parsedDates.add(displayDate(flightInfo.getScheduledDateTime()));
            if (filterByRequestedDate && !matchesRequestedDate(flightInfo.getScheduledDateTime(), date)) {
                continue;
            }
            dateMatchedFlights++;

            String uniqueKey = buildUniqueKey(flightInfo);
            if (seenKeys.add(uniqueKey)) {
                flights.add(flightInfo);
            }
        }

        System.out.println("AENA HTML candidatos crudos: " + rawCandidates
                + ", candidatos parseados: " + parsedCandidates
                + ", coinciden con fecha: " + dateMatchedFlights
                + ", parseados finales: " + flights.size()
                + ", fechas parseadas: " + parsedDates + ".");
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

    private boolean matchesRequestedDate(String scheduledDateTime, String requestedDate) {
        if (requestedDate == null || requestedDate.isBlank()) {
            return true;
        }
        if (scheduledDateTime == null || scheduledDateTime.isBlank()) {
            return false;
        }
        if (scheduledDateTime.startsWith(requestedDate)) {
            return true;
        }

        LocalDate scheduledDate = parseDate(scheduledDateTime);
        LocalDate requested = parseDate(requestedDate);
        return scheduledDate != null && scheduledDate.equals(requested);
    }

    private String describeRequest(String url, String originAirport, String destinationAirport, String date) {
        return "url=" + url
                + ", originAirport=" + display(originAirport)
                + ", destinationAirport=" + display(destinationAirport)
                + ", fecha=" + display(date);
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "N/D" : value;
    }

    private String responseType(String body, String contentType) {
        if (isJsonContent(body, contentType)) {
            return "JSON";
        }
        return "HTML";
    }

    private String displayDate(String scheduledDateTime) {
        LocalDate parsedDate = parseDate(scheduledDateTime);
        return parsedDate == null ? "sin-fecha" : parsedDate.toString();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim();
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        )) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        )) {
            try {
                return LocalDateTime.parse(text, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
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

    private record AenaEndpointRequest(String airport, String flightType) {
    }

    private record CurlResponse(String body, int statusCode) {
    }
}
