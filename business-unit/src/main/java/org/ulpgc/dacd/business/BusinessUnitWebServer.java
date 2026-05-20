package org.ulpgc.dacd.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BusinessUnitWebServer {
    private static final String LOGO_RESOURCE_PATH = "static/piopio_fon_trans.png";
    private static final String LOGO_WEB_PATH = "/static/piopio_fon_trans.png";
    private static final String AWAY_TICKET_URL = "https://tickets.oneboxtds.com/udlpdesplazamientos/events?from=2026-05-18T23:00:00.000Z&to=2026-08-31T22:59:59.999Z";

    private final DatamartRepository datamartRepository;
    private final EventStoreDatamartLoader eventStoreDatamartLoader;
    private final BusinessUnitEventSubscriber eventSubscriber;
    private final int port;
    private final ObjectMapper objectMapper;

    private HttpServer server;
    private ExecutorService executorService;

    public BusinessUnitWebServer(DatamartRepository datamartRepository,
                                 EventStoreDatamartLoader eventStoreDatamartLoader,
                                 BusinessUnitEventSubscriber eventSubscriber,
                                 int port) {
        this.datamartRepository = datamartRepository;
        this.eventStoreDatamartLoader = eventStoreDatamartLoader;
        this.eventSubscriber = eventSubscriber;
        this.port = port;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        executorService = Executors.newCachedThreadPool();
        server.setExecutor(executorService);
        createContexts();
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }

        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void createContexts() {
        server.createContext("/", this::handleHome);
        server.createContext(LOGO_WEB_PATH, this::handleLogo);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/summary", this::handleSummary);
        server.createContext("/api/matches", this::handleMatches);
        server.createContext("/api/next-trip", this::handleNextTrip);
        server.createContext("/api/destinations", this::handleDestinations);
        server.createContext("/api/flights", this::handleFlights);
        server.createContext("/api/recommendations", this::handleRecommendations);
        server.createContext("/api/reload-eventstore", this::handleReloadEventstore);
        server.createContext("/api/live-sync/start", this::handleStartLiveSync);
        server.createContext("/api/live-sync/stop", this::handleStopLiveSync);
    }

    private void handleHome(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }
        sendHtml(exchange, dashboardHtml());
    }

    private void handleLogo(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        byte[] logo = readResource(LOGO_RESOURCE_PATH);
        if (logo == null) {
            sendError(exchange, 404, "No se encontro el logo de PioPioFly.");
            return;
        }
        sendBytes(exchange, 200, "image/png", logo);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, statusResponse());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudo leer el estado: " + e.getMessage());
        }
    }

    private void handleSummary(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.getSummary());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudo leer el resumen del datamart: " + e.getMessage());
        }
    }

    private void handleMatches(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.findUpcomingAwayMatches());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron leer los partidos: " + e.getMessage());
        }
    }

    private void handleNextTrip(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.findNextAwayMatchWithFlights());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudo leer el siguiente desplazamiento: " + e.getMessage());
        }
    }

    private void handleDestinations(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.findAvailableDestinations());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron leer los destinos: " + e.getMessage());
        }
    }

    private void handleFlights(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        String destination = parseQueryParams(exchange).get("destination");
        if (destination == null || destination.isBlank()) {
            sendError(exchange, 400, "Debe indicar un aeropuerto destino.");
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.findFlightsByDestination(destination.trim()));
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron leer los vuelos: " + e.getMessage());
        }
    }

    private void handleRecommendations(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            sendJson(exchange, 200, datamartRepository.buildTravelRecommendations());
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron generar recomendaciones: " + e.getMessage());
        }
    }

    private void handleReloadEventstore(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        DatamartLoadResult result = eventStoreDatamartLoader.load();
        sendJson(exchange, 200, result);
    }

    private void handleStartLiveSync(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        boolean alreadyActive = eventSubscriber.isActive();
        boolean started = alreadyActive ? false : eventSubscriber.start();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("started", started);
        response.put("alreadyActive", alreadyActive);
        response.put("liveSyncActive", eventSubscriber.isActive());
        response.put("message", started
                ? "Sincronizacion en vivo iniciada."
                : "La sincronizacion en vivo ya estaba activa.");
        sendJson(exchange, 200, response);
    }

    private void handleStopLiveSync(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        boolean wasActive = eventSubscriber.isActive();
        if (wasActive) {
            eventSubscriber.stop();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stopped", wasActive);
        response.put("alreadyStopped", !wasActive);
        response.put("liveSyncActive", eventSubscriber.isActive());
        response.put("message", wasActive
                ? "Sincronizacion en vivo detenida."
                : "La sincronizacion en vivo ya estaba detenida.");
        sendJson(exchange, 200, response);
    }

    private Map<String, Object> statusResponse() throws SQLException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("brokerUrl", BusinessUnitConfig.BROKER_URL);
        response.put("clientId", BusinessUnitConfig.CLIENT_ID);
        response.put("eventStoreBasePath", BusinessUnitConfig.EVENT_STORE_BASE_PATH);
        response.put("datamartDatabaseUrl", BusinessUnitConfig.DATAMART_DATABASE_URL);
        response.put("liveSyncActive", eventSubscriber.isActive());
        response.put("summary", datamartRepository.getSummary());
        return response;
    }

    private boolean isMethod(HttpExchange exchange, String expectedMethod) {
        return expectedMethod.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Metodo HTTP no permitido.");
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object value) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        byte[] response = objectMapper.writeValueAsBytes(value);
        sendBytes(exchange, statusCode, "application/json; charset=utf-8", response);
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        sendBytes(exchange, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> response = Map.of("error", message);
        sendJson(exchange, statusCode, response);
    }

    private void sendBytes(HttpExchange exchange, int statusCode, String contentType, byte[] response)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private byte[] readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return inputStream.readAllBytes();
        }
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return params;
        }

        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String dashboardHtml() {
        return """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>PioPioFly Business Unit</title>
                    <style>
                        :root {
                            --yellow: #FFD400;
                            --soft-yellow: #FFF4B8;
                            --blue: #0057B8;
                            --dark-blue: #003B7A;
                            --background: #FFFFFF;
                            --secondary: #F7FAFF;
                            --border: #D8E6FF;
                            --text: #10213D;
                            --muted: #61708A;
                        }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            font-family: "Montserrat", "Aptos", "Segoe UI", sans-serif;
                            color: var(--text);
                            background: var(--background);
                        }
                        header {
                            position: relative;
                            overflow: hidden;
                            color: var(--dark-blue);
                            background:
                                linear-gradient(135deg, rgba(255, 255, 255, 0.18), transparent 35%),
                                linear-gradient(90deg, var(--yellow), #FFE45C);
                            border-bottom: 14px solid var(--blue);
                        }
                        header::before {
                            content: "";
                            position: absolute;
                            left: 0;
                            right: 0;
                            top: 0;
                            height: 18px;
                            background: repeating-linear-gradient(
                                115deg,
                                var(--blue) 0 34px,
                                transparent 34px 66px
                            );
                        }
                        header::after {
                            content: "";
                            position: absolute;
                            inset: auto -8vw -42px auto;
                            width: 36vw;
                            height: 36vw;
                            min-width: 280px;
                            min-height: 280px;
                            border-radius: 999px;
                            background: rgba(0, 87, 184, 0.10);
                        }
                        .hero {
                            position: relative;
                            z-index: 1;
                            display: grid;
                            grid-template-columns: minmax(260px, auto) minmax(0, 1fr);
                            align-items: center;
                            gap: clamp(28px, 5vw, 72px);
                            max-width: 1380px;
                            margin: 0 auto;
                            padding: 64px clamp(22px, 5vw, 80px) 56px;
                        }
                        .logo-frame {
                            width: clamp(260px, 30vw, 430px);
                            height: clamp(260px, 30vw, 430px);
                            overflow: hidden;
                            background: rgba(255, 255, 255, 0.28);
                            border: 4px solid rgba(0, 87, 184, 0.14);
                            border-radius: 52px;
                            box-shadow: 0 24px 54px rgba(0, 59, 122, 0.18);
                            display: grid;
                            place-items: center;
                        }
                        .logo {
                            width: 100%;
                            height: 100%;
                            object-fit: contain;
                            transform: scale(1.28);
                        }
                        h1, h2, h3 { margin: 0; }
                        h1 {
                            color: var(--blue);
                            font-size: clamp(4rem, 10vw, 9rem);
                            letter-spacing: -0.09em;
                            line-height: 0.86;
                            text-transform: uppercase;
                        }
                        .subtitle {
                            max-width: 720px;
                            margin-top: 18px;
                            color: var(--dark-blue);
                            font-size: clamp(1.25rem, 2.25vw, 1.9rem);
                            font-weight: 900;
                        }
                        .hero-copy {
                            display: grid;
                            gap: 22px;
                            justify-items: start;
                        }
                        .hero-pill {
                            display: inline-flex;
                            align-items: center;
                            width: fit-content;
                            border-radius: 999px;
                            padding: 10px 16px;
                            background: var(--blue);
                            color: white;
                            font-weight: 900;
                            letter-spacing: 0.06em;
                            text-transform: uppercase;
                            font-size: 0.82rem;
                        }
                        main {
                            width: min(1320px, calc(100% - 32px));
                            margin: 34px auto 60px;
                            display: grid;
                            gap: 28px;
                        }
                        .two-columns {
                            display: grid;
                            grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.25fr);
                            gap: 28px;
                        }
                        .card {
                            background: #FFFFFF;
                            border: 1px solid var(--border);
                            border-radius: 30px;
                            box-shadow: 0 18px 50px rgba(0, 59, 122, 0.11);
                            padding: clamp(22px, 3vw, 34px);
                        }
                        .highlight-card {
                            border: 0;
                            color: var(--dark-blue);
                            background:
                                linear-gradient(135deg, rgba(255,255,255,0.76), rgba(255,255,255,0.42)),
                                linear-gradient(120deg, var(--yellow), #FFE777);
                        }
                        .section-title {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            gap: 12px;
                            margin-bottom: 20px;
                        }
                        .section-title h2 {
                            color: var(--blue);
                            font-size: clamp(1.45rem, 2.2vw, 2.2rem);
                            font-weight: 950;
                            letter-spacing: -0.04em;
                        }
                        .button-link {
                            display: inline-flex;
                            justify-content: center;
                            align-items: center;
                            text-decoration: none;
                            border: 0;
                            border-radius: 999px;
                            padding: 13px 20px;
                            background: var(--blue);
                            color: white;
                            font-weight: 900;
                            cursor: pointer;
                            box-shadow: 0 14px 28px rgba(0, 87, 184, 0.24);
                        }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            overflow: hidden;
                            border-radius: 16px;
                        }
                        th, td {
                            padding: 14px 12px;
                            border-bottom: 1px solid #EEF4FF;
                            text-align: left;
                            vertical-align: top;
                        }
                        th {
                            color: var(--dark-blue);
                            background: #EEF5FF;
                            font-size: 0.82rem;
                            text-transform: uppercase;
                            letter-spacing: 0.04em;
                        }
                        .empty, .message {
                            color: var(--muted);
                            padding: 12px 0;
                        }
                        .next-trip {
                            display: grid;
                            gap: 16px;
                        }
                        .next-trip h3 {
                            color: var(--dark-blue);
                            font-size: clamp(2.1rem, 4vw, 3.8rem);
                            letter-spacing: -0.06em;
                            line-height: 0.95;
                        }
                        .detail-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                            gap: 10px;
                            margin: 12px 0;
                        }
                        .detail {
                            background: rgba(255, 255, 255, 0.64);
                            border: 1px solid rgba(0, 87, 184, 0.12);
                            border-radius: 18px;
                            padding: 14px;
                        }
                        .detail span {
                            display: block;
                            color: var(--blue);
                            font-size: 0.8rem;
                            margin-bottom: 4px;
                            font-weight: 900;
                            text-transform: uppercase;
                            letter-spacing: 0.05em;
                        }
                        .match-list {
                            display: grid;
                            gap: 12px;
                        }
                        .match-row {
                            display: grid;
                            grid-template-columns: minmax(130px, 0.7fr) minmax(220px, 1.5fr) minmax(120px, 0.7fr);
                            gap: 14px;
                            align-items: center;
                            padding: 16px;
                            border: 1px solid var(--border);
                            border-radius: 20px;
                            background: var(--secondary);
                        }
                        .match-row strong {
                            color: var(--dark-blue);
                            font-size: 1.05rem;
                        }
                        .flight-action {
                            white-space: nowrap;
                        }
                        .flight-section {
                            display: grid;
                            gap: 12px;
                        }
                        .flight-section + .flight-section {
                            margin-top: 26px;
                            padding-top: 22px;
                            border-top: 1px solid var(--border);
                        }
                        .flight-section h3 {
                            color: var(--dark-blue);
                            font-size: 1.2rem;
                            font-weight: 950;
                        }
                        .search-actions {
                            display: flex;
                            flex-wrap: wrap;
                            gap: 10px;
                            margin-top: 12px;
                        }
                        .small { color: var(--muted); font-size: 0.92rem; }
                        @media (max-width: 920px) {
                            .two-columns { grid-template-columns: 1fr; }
                            .match-row { grid-template-columns: 1fr; }
                        }
                        @media (max-width: 720px) {
                            .hero { grid-template-columns: 1fr; text-align: center; justify-items: center; }
                            .hero-copy { justify-items: center; }
                            .logo-frame { width: 280px; height: 280px; border-radius: 40px; }
                            h1 { font-size: clamp(3.4rem, 16vw, 5rem); }
                            table { display: block; overflow-x: auto; }
                        }
                    </style>
                </head>
                <body>
                    <header>
                        <div class="hero">
                            <div class="logo-frame">
                                <img class="logo" src="/static/piopio_fon_trans.png" alt="PioPioFly">
                            </div>
                            <div class="hero-copy">
                                <span class="hero-pill">¡Arriba d'ellos!</span>
                                <h1>PioPioFly</h1>
                                <p class="subtitle">Tu asistente de desplazamientos UD Las Palmas</p>
                            </div>
                        </div>
                    </header>

                    <main>
                        <section class="card">
                            <div class="section-title"><h2>Pr&oacute;ximos partidos fuera de casa</h2></div>
                            <div id="matchesList"></div>
                        </section>

                        <section class="two-columns">
                            <article class="card highlight-card">
                                <div class="section-title"><h2>Siguiente desplazamiento</h2></div>
                                <div id="nextTrip"></div>
                            </article>
                            <article class="card">
                                <div class="section-title"><h2>Vuelos del desplazamiento</h2></div>
                                <div id="nextTripFlights"></div>
                            </article>
                        </section>
                    </main>

                    <script>
                        const awayTicketUrl = "__AWAY_TICKET_URL__";

                        function display(value) {
                            return value === null || value === undefined || String(value).trim() === '' ? 'N/D' : value;
                        }

                        async function requestJson(url, options = {}) {
                            const response = await fetch(url, options);
                            const data = await response.json();
                            if (!response.ok) {
                                throw new Error('No se pudieron cargar los datos. Intentalo de nuevo mas tarde.');
                            }
                            return data;
                        }

                        async function loadMatches() {
                            const matches = uniqueMatches(await requestJson('/api/matches'));
                            const container = document.getElementById('matchesList');
                            if (!matches.length) {
                                container.innerHTML = '<p class="empty">Todav&iacute;a no hay partidos fuera de casa cargados.</p>';
                                return;
                            }
                            container.innerHTML = `<div class="match-list">${matches.map(renderMatchRow).join('')}</div>`;
                        }

                        async function loadNextTrip() {
                            const trip = await requestJson('/api/next-trip');
                            renderNextTrip(trip);
                            renderNextTripFlights(trip);
                        }

                        function renderNextTrip(trip) {
                            const container = document.getElementById('nextTrip');
                            if (!trip.match) {
                                container.innerHTML = '<p class="empty">Todav&iacute;a no hay partidos fuera de casa cargados.</p>';
                                return;
                            }

                            const match = trip.match;
                            container.innerHTML = `
                                <div class="next-trip">
                                    <h3>${escapeHtml(display(match.homeTeam))} vs ${escapeHtml(display(match.awayTeam))}</h3>
                                    <div class="detail-grid">
                                        <div class="detail"><span>Fecha</span><strong>${escapeHtml(display(match.matchDate))}</strong></div>
                                        <div class="detail"><span>Ciudad</span><strong>${escapeHtml(display(match.city))}</strong></div>
                                        <div class="detail"><span>Estadio</span><strong>${escapeHtml(display(match.stadium))}</strong></div>
                                        <div class="detail"><span>Aeropuerto destino</span><strong>${escapeHtml(display(match.destinationAirport))}</strong></div>
                                    </div>
                                    <a class="button-link" href="${awayTicketUrl}" target="_blank" rel="noopener noreferrer">Comprar entrada</a>
                                </div>`;
                        }

                        function renderNextTripFlights(trip) {
                            const container = document.getElementById('nextTripFlights');
                            if (!trip.match) {
                                container.innerHTML = '<p class="empty">Todav&iacute;a no hay partidos fuera de casa cargados.</p>';
                                return;
                            }

                            if (trip.invalidMatchDate) {
                                container.innerHTML = '<p class="empty">No se puede calcular la ventana de vuelos porque la fecha del partido no es v&aacute;lida.</p>';
                                return;
                            }

                            const outboundFlights = uniqueFlights(trip.outboundFlights || []);
                            const returnFlights = uniqueFlights(trip.returnFlights || []);
                            const destinationAirport = cleanValue(trip.match.destinationAirport);
                            container.innerHTML = `
                                ${renderFlightSection(
                                    'Vuelos de ida',
                                    `Buscando vuelos de ida entre ${display(trip.outboundWindowStart)} y ${display(trip.outboundWindowEnd)}`,
                                    outboundFlights,
                                    'No hay vuelos de ida cargados desde AENA para estas fechas.',
                                    [
                                        {
                                            label: 'Buscar ida 2 d&iacute;as antes',
                                            origin: 'LPA',
                                            destination: destinationAirport,
                                            date: cleanValue(trip.outboundWindowStart)
                                        },
                                        {
                                            label: 'Buscar ida 1 d&iacute;a antes',
                                            origin: 'LPA',
                                            destination: destinationAirport,
                                            date: cleanValue(trip.outboundWindowEnd)
                                        }
                                    ]
                                )}
                                ${renderFlightSection(
                                    'Vuelos de vuelta',
                                    `Buscando vuelos de vuelta para ${display(trip.returnDate)}`,
                                    returnFlights,
                                    'No hay vuelos de vuelta cargados desde AENA para el d&iacute;a siguiente al partido.',
                                    [
                                        {
                                            label: 'Buscar vuelta',
                                            origin: destinationAirport,
                                            destination: 'LPA',
                                            date: cleanValue(trip.returnDate)
                                        }
                                    ]
                                )}
                            `;
                        }

                        function renderFlightSection(title, helperText, flights, emptyMessage, searchActions = []) {
                            const content = flights.length
                                ? table(
                                ['Fecha/hora', 'Vuelo', 'Aerolinea', 'Origen', 'Destino', 'Estado', 'Terminal', 'Accion'],
                                flights.map(flight => [
                                    display(flight.scheduledDateTime),
                                    display(flight.flightNumber),
                                    display(flight.airline),
                                    display(flight.originAirport),
                                    display(flight.destinationAirport),
                                    display(flight.status),
                                    display(flight.terminal),
                                    `<a class="button-link" href="${buildSpecificFlightSearchUrl(flight)}" target="_blank" rel="noopener noreferrer">Ver vuelo</a>`
                                ]),
                                true
                            )
                                : `<p class="empty">${emptyMessage}</p>${renderSearchActions(searchActions)}`;

                            return `
                                <section class="flight-section">
                                    <h3>${escapeHtml(title)}</h3>
                                    <p class="small">${escapeHtml(helperText)}</p>
                                    ${content}
                                </section>
                            `;
                        }

                        function renderSearchActions(actions) {
                            const links = actions
                                .filter(action => action.origin && action.destination && action.date)
                                .map(action => `<a class="button-link" href="${buildFlightSearchUrl(action.origin, action.destination, action.date)}" target="_blank" rel="noopener noreferrer">${action.label}</a>`);

                            if (!links.length) {
                                return '';
                            }

                            return `<div class="search-actions">${links.join('')}</div>`;
                        }

                        async function refreshAll() {
                            try {
                                await Promise.all([
                                    loadMatches(),
                                    loadNextTrip()
                                ]);
                            } catch (error) {
                                document.getElementById('matchesList').innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
                                document.getElementById('nextTrip').innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
                                document.getElementById('nextTripFlights').innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
                            }
                        }

                        function renderMatchRow(match) {
                            return `
                                <article class="match-row">
                                    <strong>${escapeHtml(display(match.matchDate))}</strong>
                                    <div>${escapeHtml(display(match.homeTeam))} vs ${escapeHtml(display(match.awayTeam))}</div>
                                    <span class="small">${escapeHtml(display(match.destinationAirport))}</span>
                                </article>`;
                        }

                        function table(headers, rows, allowHtml = false) {
                            return `<table><thead><tr>${headers.map(header => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead><tbody>${
                                rows.map(row => `<tr>${row.map(cell => `<td>${allowHtml && String(cell).startsWith('<a ') ? cell : escapeHtml(cell)}</td>`).join('')}</tr>`).join('')
                            }</tbody></table>`;
                        }

                        function buildFlightSearchUrl(origin, destination, date) {
                            const query = `Google Flights ${origin} to ${destination} ${date}`;
                            return `https://www.google.com/search?q=${encodeURIComponent(query)}`;
                        }

                        function buildSpecificFlightSearchUrl(flight) {
                            const flightNumber = getFlightValue(flight, 'flightNumber', 'flight_number');
                            const airline = getFlightValue(flight, 'airline', 'airline');
                            const origin = getFlightValue(flight, 'originAirport', 'origin_airport');
                            const destination = getFlightValue(flight, 'destinationAirport', 'destination_airport');
                            const scheduledDateTime = getFlightValue(flight, 'scheduledDateTime', 'scheduled_datetime');
                            const date = scheduledDateTime && scheduledDateTime.length >= 10
                                ? scheduledDateTime.substring(0, 10)
                                : '';

                            const queryParts = [
                                'Google Flights',
                                flightNumber,
                                airline,
                                origin,
                                'to',
                                destination,
                                date
                            ].filter(Boolean);

                            return `https://www.google.com/search?q=${encodeURIComponent(queryParts.join(' '))}`;
                        }

                        function getFlightValue(flight, camelCaseName, snakeCaseName) {
                            return cleanValue(flight[camelCaseName] ?? flight[snakeCaseName]);
                        }

                        function cleanValue(value) {
                            if (value === null || value === undefined) {
                                return '';
                            }
                            const text = String(value).trim();
                            if (text === 'null' || text === 'undefined') {
                                return '';
                            }
                            return text;
                        }

                        function uniqueMatches(matches) {
                            const byKey = new Map();
                            matches.forEach(match => byKey.set(matchKey(match), match));
                            return Array.from(byKey.values());
                        }

                        function matchKey(match) {
                            return `${normalize(match.homeTeam)}|${normalize(match.awayTeam)}|${normalizeDateTime(match.matchDate)}|${normalize(match.stadium)}|${normalize(match.destinationAirport)}`;
                        }

                        function uniqueFlights(flights) {
                            const byKey = new Map();
                            flights.forEach(flight => byKey.set(flightKey(flight), flight));
                            return Array.from(byKey.values());
                        }

                        function flightKey(flight) {
                            return `${normalize(flight.flightNumber)}|${normalize(flight.originAirport)}|${normalize(flight.destinationAirport)}|${normalizeDateTime(flight.scheduledDateTime)}`;
                        }

                        function normalize(value) {
                            return value === null || value === undefined ? '' : String(value).trim().toLowerCase();
                        }

                        function normalizeDateTime(value) {
                            if (value === null || value === undefined || String(value).trim() === '') {
                                return '';
                            }
                            const date = new Date(value);
                            return Number.isNaN(date.getTime()) ? normalize(value) : date.toISOString().slice(0, 16);
                        }

                        function escapeHtml(value) {
                            return String(value)
                                .replaceAll('&', '&amp;')
                                .replaceAll('<', '&lt;')
                                .replaceAll('>', '&gt;')
                                .replaceAll('"', '&quot;')
                                .replaceAll("'", '&#039;');
                        }

                        refreshAll();
                    </script>
                </body>
                </html>
                """.replace("__AWAY_TICKET_URL__", AWAY_TICKET_URL);
    }
}